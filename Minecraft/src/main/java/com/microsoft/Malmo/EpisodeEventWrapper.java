package com.microsoft.Malmo;

import java.util.concurrent.locks.ReentrantReadWriteLock;

import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent.PotentialSpawns;
import net.minecraftforge.fml.client.event.ConfigChangedEvent.OnConfigChangedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Class that is responsible for catching all the Forge events we require (server ticks, client ticks, etc)
 * and passing them on to the current episode.<br>
 * Doing it this way saves us having to register/deregister each individual episode, which was causing race-condition vulnerabilities.
 */
public class EpisodeEventWrapper {
    /** The current episode, if there is one. */
    private StateEpisode stateEpisode = null;

    /** Lock to prevent the state episode being changed whilst mid event.<br>
     * This does not prevent multiple events from *reading* the stateEpisode, but
     * it won't allow *writing* to the stateEpisode whilst it is being read.
     */
    ReentrantReadWriteLock stateEpisodeLock = new ReentrantReadWriteLock();

    /** Set our state to a new episode.<br>
     * This waits on the stateEpisodeLock to prevent the episode being changed whilst in use.
     * @param stateEpisode the episode to switch to.
     * @return the previous state episode.
     */
    public StateEpisode setStateEpisode(StateEpisode stateEpisode)
    {
        this.stateEpisodeLock.writeLock().lock();
    	StateEpisode lastEpisode = this.stateEpisode;
        this.stateEpisode = stateEpisode;
        this.stateEpisodeLock.writeLock().unlock();
        return lastEpisode;
    }
    
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent ev)
    {
        // Now pass the event on to the active episode, if there is one:
        this.stateEpisodeLock.readLock().lock();
        if (this.stateEpisode != null && this.stateEpisode.isLive())
        {
        	try
        	{
        		this.stateEpisode.onClientTick(ev);
        	}
        	catch (Exception e)
        	{
        		// Do what??
        	}
        }
        this.stateEpisodeLock.readLock().unlock();
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent ev)
    {
        // Pass the event on to the active episode, if there is one:
        this.stateEpisodeLock.readLock().lock();
        if (this.stateEpisode != null && this.stateEpisode.isLive())
        {
            this.stateEpisode.onServerTick(ev);
        }
        this.stateEpisodeLock.readLock().unlock();
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent ev)
    {
        // Pass the event on to the active episode, if there is one:
        this.stateEpisodeLock.readLock().lock();
        if (this.stateEpisode != null && this.stateEpisode.isLive())
        {
            this.stateEpisode.onPlayerTick(ev);
        }
        this.stateEpisodeLock.readLock().unlock();
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent ev)
    {
        // Pass the event on to the active episode, if there is one:
        this.stateEpisodeLock.readLock().lock();
        if (this.stateEpisode != null && this.stateEpisode.isLive())
        {
            this.stateEpisode.onRenderTick(ev);
        }
        this.stateEpisodeLock.readLock().unlock();
    }

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load cev)
    {
        // Pass the event on to the active episode, if there is one:
        this.stateEpisodeLock.readLock().lock();
        if (this.stateEpisode != null && this.stateEpisode.isLive())
        {
            this.stateEpisode.onChunkLoad(cev);
        }
        this.stateEpisodeLock.readLock().unlock();
    }

    @SubscribeEvent
    public void onPlayerDies(LivingDeathEvent lde)
    {
        // Pass the event on to the active episode, if there is one:
        this.stateEpisodeLock.readLock().lock();
        if (this.stateEpisode != null && this.stateEpisode.isLive())
        {
            this.stateEpisode.onPlayerDies(lde);
        }
        this.stateEpisodeLock.readLock().unlock();
    }
    
    @SubscribeEvent
    public void onConfigChanged(OnConfigChangedEvent ev)
    {
    	if (ev.modID == MalmoMod.MODID)	// Check we are responding to the correct Mod's event!
    	{
    		this.stateEpisodeLock.readLock().lock();
    		if (this.stateEpisode != null && this.stateEpisode.isLive())
    		{
    			this.stateEpisode.onConfigChanged(ev);
    		}
    		else
    		{
    			//TODO - should we make sure this config change is acted on?
    		}
    		this.stateEpisodeLock.readLock().unlock();
    	}
    }
    
    @SubscribeEvent
    public void onPlayerJoinedServer(PlayerLoggedInEvent ev)
    {
    	this.stateEpisodeLock.readLock().lock();
    	if (this.stateEpisode != null && this.stateEpisode.isLive())
    	{
    		this.stateEpisode.onPlayerJoinedServer(ev);
    	}
    	this.stateEpisodeLock.readLock().unlock();
    }
    
    /** Called by Forge - call setCanceled(true) to prevent spawning in our world.*/
    @SubscribeEvent
    public void onGetPotentialSpawns(PotentialSpawns ps)
    {
    	this.stateEpisodeLock.readLock().lock();
    	if (this.stateEpisode != null && this.stateEpisode.isLive())
    	{
    		this.stateEpisode.onGetPotentialSpawns(ps);
    	}
    	this.stateEpisodeLock.readLock().unlock();
    }
}
