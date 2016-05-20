package com.microsoft.Malmo.MissionHandlers;

import io.netty.buffer.ByteBuf;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.IThreadListener;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.microsoft.Malmo.MalmoMod;
import com.microsoft.Malmo.MalmoMod.MalmoMessageType;
import com.microsoft.Malmo.MalmoMod.IMalmoMessageListener;
import com.microsoft.Malmo.MissionHandlerInterfaces.IObservationProducer;
import com.microsoft.Malmo.Schemas.MissionInit;

/** Starting-point for observation producers that need to deal with extracting information from the server.<br>
 * It's hard to wrap this stuff cleanly, since the code which actually creates the JSON needs to be executed on the server, and may not
 * have any access to the rest of the code...
 *
 * To use:
 * a) Extend ObservationFromServer. (Make createObservationRequestMessage() return an instance of the object described in step b.)
 * b) Extend ObservationRequestMessage and implement the persist/restore methods to encode the context needed for the observations (eg size of grid to be returned, etc).
 * c) Extend ObservationRequestMessageHandler.
 *       - Make sure it implements IMessageHandler<yourRequestMessage, IMessage>
 *       - Make sure it calls processMessage() in the onMessage() method
 *       - Put your actual JSON production code in the buildJson() method
 * d) Add a call to register the message in MalmoMod.preInit()
 *       eg: network.registerMessage(yourClass.yourMessageHandler.class, yourClass.yourMessage.class, 1, Side.SERVER);
 * e) Make sure prepare() and cleanup() call super.prepare() and super.cleanup()
 */


public abstract class ObservationFromServer extends HandlerBase implements IMalmoMessageListener, IObservationProducer
{
	private String latestJsonStats = "";
	private boolean missionIsRunning = false;
	
	ObservationFromServer()
	{
		// Register for client ticks so we can keep requesting stats.
		FMLCommonHandler.instance().bus().register(this);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent ev)
    {
    	if (this.missionIsRunning)
    	{
	    	// Use the client tick to fire messages to the server to request up-to-date stats.
	    	// We can then use those stats to fire back to the agent in writeObservationsToJSON.
    		ObservationRequestMessage message = createObservationRequestMessage();
    		// To make sure only the intended listener receives this message, set the id now:
    		message.id = System.identityHashCode(this);
	    	MalmoMod.network.sendToServer(message);
    	}
    }

	@Override
	public void prepare(MissionInit missionInit)
	{
		this.missionIsRunning = true;	// Will start us asking the server for stats.
		MalmoMod.MalmoMessageHandler.registerForMessage(this, MalmoMessageType.SERVER_OBSERVATIONSREADY);
	}

	@Override
	public void cleanup()
	{
		this.missionIsRunning = false;	// Stop asking for stats.
		MalmoMod.MalmoMessageHandler.deregisterForMessage(this, MalmoMessageType.SERVER_OBSERVATIONSREADY);
	}

    @Override
	public void writeObservationsToJSON(JsonObject json, MissionInit missionInit)
	{
    	String jsonstring = "";
    	synchronized (this.latestJsonStats)
    	{
    		jsonstring = this.latestJsonStats;
		}
    	if (jsonstring.length() > 2)	// "{}" is the empty JSON string.
    	{
    		// Parse the string into json:
    		JsonParser parser = new JsonParser();
    		JsonElement root = parser.parse(jsonstring);
    		// Now copy the children of root into the provided json object:
    		if (root.isJsonObject())
    		{
    			JsonObject rootObj = root.getAsJsonObject();
				for (Map.Entry<String, JsonElement> entry : rootObj.entrySet())
				{
					json.add(entry.getKey(), entry.getValue());
				}
    		}
    	}
	}

    @Override
	public void onMessage(MalmoMessageType messageType, Map<String, String> data)
	{
    	if (data != null)
    	{
	    	synchronized (this.latestJsonStats)
	    	{
	    		this.latestJsonStats = data.get("json");
	    		onReturnedData(data);
			}
    	}
	}

    /** Override this to act on any extra data returned by the server.<br>
     * @param data
     */
    protected void onReturnedData(Map<String, String> data)
    {
	}

	public abstract ObservationRequestMessage createObservationRequestMessage();
	
    /** Tiny message class for requesting observational data from the server.<br>
     * Contains just an id string, used to map the request to the object which can fulfil it.
     * Subclasses of ObservationFromServer should subclass this to include all the state which is required to process their request.
     */
    public abstract static class ObservationRequestMessage implements IMessage
    {
    	/** Identifier of the listener that will be responding to this request.*/
    	private int id = 0;

    	public ObservationRequestMessage()
    	{
    	}

    	public ObservationRequestMessage(int id)
    	{
    		this.id = id;
    	}
    	
    	@Override
    	public final void fromBytes(ByteBuf buf)
    	{

    		this.id = buf.readInt();
    		restoreState(buf);
    	}

    	@Override
    	public final void toBytes(ByteBuf buf)
    	{
    		// Subclasses MUST call this
    		buf.writeInt(this.id);
    		persistState(buf);
    	}
    	
    	abstract void restoreState(ByteBuf buf);
    	abstract void persistState(ByteBuf buf);

		/** Override this if you want to return some extra data back to the client thread.<br>
		 * Any objects created MUST be serialisable, as they are returned by serialising/deserialising the map.
		 */
		public void addReturnData(Map<String, String> returnData)
		{
		}
    }
	
    /** Simple handler to process the request message.<br>
     * REMEMBER that all this code exists on the server-side (it might not even have been compiled on the client side).
     * Any state required must be passed through the message.
     */
    public abstract static class ObservationRequestMessageHandler
	{
		public ObservationRequestMessageHandler()
		{
		}
		
		/** IMPORTANT: Call this from the onMessage method in the subclass. */
		public IMessage processMessage(final ObservationRequestMessage message, final MessageContext ctx)
		{
			IThreadListener mainThread = (WorldServer)ctx.getServerHandler().playerEntity.worldObj;
			mainThread.addScheduledTask(new Runnable() {
				@Override
				public void run() {
					EntityPlayerMP player = ctx.getServerHandler().playerEntity;
					JsonObject json = new JsonObject();
					buildJson(json, player, message, ctx);
					// Send this message back again now we've filled in the json stats.
					Map<String, String> returnData = new HashMap<String, String>();
					returnData.put("json", json.toString());
					message.addReturnData(returnData);
					MalmoMod.network.sendTo(new MalmoMod.MalmoMessage(MalmoMessageType.SERVER_OBSERVATIONSREADY, message.id, returnData), player);
				}
			});
			return null; // no response in this case
		}
		
		/** Build the JSON observation that has been requested by the message.
		 */
		abstract void buildJson(JsonObject json, EntityPlayerMP player, ObservationRequestMessage message, MessageContext ctx);
    }
}
