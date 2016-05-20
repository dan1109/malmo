package com.microsoft.Malmo.Client;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.Display;

import net.minecraft.client.Minecraft;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import com.microsoft.Malmo.MissionHandlerInterfaces.IVideoProducer;
import com.microsoft.Malmo.Schemas.ClientAgentConnection;
import com.microsoft.Malmo.Schemas.MissionInit;
import com.microsoft.Malmo.Utils.TCPSocketHelper;

/**
 * Register this class on the MinecraftForge.EVENT_BUS to intercept video
 * frames.
 * <p>
 * We use this to send video frames over sockets.
 */
public class VideoHook {
    /**
     * If the sockets are not yet open we delay before retrying. Value is in
     * nanoseconds.
     */
    private static final long RETRY_GAP_NS = 5000000000L;

    /**
     * The time in nanoseconds after which we should try sending again.
     */
    private long retry_time_ns = 0;

    /**
     * Calling stop() if we're not running is a no-op.
     */
    private boolean isRunning = false;

    /**
     * MissionInit object for passing to the IVideoProducer.
     */
    private MissionInit missionInit;
    
    /**
     * Object that will provide the actual video frame on demand.
     */
    private IVideoProducer videoProducer;
    
    /**
     * Public count of consecutive TCP failures - used to terminate a mission if nothing is listening
     */
    public int failedTCPSendCount = 0;
    
	/**
	 * Object which maintains our connection to the agent.
	 */
	private TCPSocketHelper.SocketChannelHelper connection = null;

    ByteBuffer buffer = null;
    
    /**
     * Resize the rendering and start sending video over TCP.
     */
    public void start(MissionInit missionInit, IVideoProducer videoProducer)
    {
        if (videoProducer == null)
        {
            return; // Don't start up if there is nothing to provide the video.
        }

        videoProducer.prepare(missionInit);
        this.missionInit = missionInit;
        this.videoProducer = videoProducer;
        this.buffer = BufferUtils.createByteBuffer(this.videoProducer.getRequiredBufferSize());

        int width = videoProducer.getWidth(missionInit);
        int height = videoProducer.getHeight(missionInit);
        forceResize(width, height);
        Display.setResizable(false); 
        // We attempt to prevent the window from being resizing during a mission 
        // because it would degrade the video quality since the rendered image would
        // be squashed.
        // TODO: Prevent F11 from making full-screen while a mission is running.

        ClientAgentConnection cac = missionInit.getClientAgentConnection();
        if (cac == null)
        	return;	// Don't start up if we don't have any connection details.

        String agentIPAddress = cac.getAgentIPAddress();
        int agentPort = cac.getAgentVideoPort();

        this.connection = new TCPSocketHelper.SocketChannelHelper(agentIPAddress, agentPort);
        this.failedTCPSendCount = 0;
        
        try
        {
            MinecraftForge.EVENT_BUS.register(this);
        }
        catch(Exception e)
        {
            System.out.println("Failed to register video hook: " + e);
        }
        this.isRunning = true;
    }

    /**
     * Stop sending video.
     */
    public void stop()
    {
        if( !this.isRunning )
        {
            return;
        }
        if (this.videoProducer != null)
        	this.videoProducer.cleanup();
        
        // stop sending video frames
        try
        {
            MinecraftForge.EVENT_BUS.unregister(this);
        }
        catch(Exception e)
        {
            System.out.println("Failed to unregister video hook: " + e);
        }
        // Close our TCP socket:
        this.connection.close();
        this.isRunning = false;
        
        // allow the user to resize the window again
        Display.setResizable(true);
    }

    /**
     * Called when the world has been rendered but not yet the GUI or player hand.
     * 
     * @param event
     *            Contains information about the event (not used).
     */
    @SubscribeEvent
    public void onRender(RenderWorldLastEvent event)
    {
        long time_before_ns = System.nanoTime();

        if (time_before_ns < retry_time_ns)
            return;

        try
        {
            int size = this.videoProducer.getRequiredBufferSize();
            // Get buffer ready for writing to:
            this.buffer.clear();
            // Write the frame:
            this.videoProducer.getFrame(this.missionInit, this.buffer);
            long time_after_render_ns = System.nanoTime();
            // The buffer gets flipped by getFrame(), so now we can simply write the frame to the socket:
            this.connection.sendTCPBytes(this.buffer, size);

            long time_after_ns = System.nanoTime();
            float ms_send = (time_after_ns - time_after_render_ns) / 1000000.0f;
            float ms_render = (time_after_render_ns - time_before_ns) / 1000000.0f;
            this.failedTCPSendCount = 0;    // Reset count of failed sends.
//            System.out.format("Total: %.2fms; collecting took %.2fms; sending %d bytes took %.2fms\n", ms_send + ms_render, ms_render, size, ms_send);
//            System.out.println("Collect: " + ms_render + "; Send: " + ms_send);
        }
        catch (Exception e)
        {
            System.out.format("Failed to send frame to TCP connection.");
            System.out.format("Will retry in %d seconds\n", RETRY_GAP_NS / 1000000000L);
            retry_time_ns = time_before_ns + RETRY_GAP_NS;
            this.failedTCPSendCount++;
        }
    }

    /** Force Minecraft to resize its GUI
     * @param width new width of window
     * @param height new height of window
     */
    private void forceResize(int width, int height)
    {
        // Are we in the dev environment or deployed?
        boolean devEnv = (Boolean) Launch.blackboard.get("fml.deobfuscatedEnvironment");
        // We need to know, because the method name will either be obfuscated or not.
        String resizeMethodName = devEnv ? "resize" : "func_71370_a";

        Class[] cArgs = new Class[2];
        cArgs[0] = int.class;
        cArgs[1] = int.class;
        Method resize;
        try
        {
            resize = Minecraft.class.getDeclaredMethod(resizeMethodName, cArgs);
            resize.setAccessible(true);
            resize.invoke(Minecraft.getMinecraft(), width, height);
        }
        catch (NoSuchMethodException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch (SecurityException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch (IllegalAccessException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch (IllegalArgumentException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch (InvocationTargetException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
 }
