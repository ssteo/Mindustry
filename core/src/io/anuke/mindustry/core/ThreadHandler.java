package io.anuke.mindustry.core;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.TimeUtils;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.util.Log;

import static io.anuke.mindustry.Vars.logic;

public class ThreadHandler {
    private final ThreadProvider impl;
    private float delta = 1f;
    private boolean enabled;

    private final Object updateLock = new Object();
    private boolean rendered = true;

    public ThreadHandler(ThreadProvider impl){
        this.impl = impl;

        Timers.setDeltaProvider(() -> impl.isOnThread() ? delta : Gdx.graphics.getDeltaTime()*60f);
    }

    public void handleRender(){
        if(!enabled) return;

        synchronized (updateLock) {
            rendered = true;
            updateLock.notify();
        }
    }

    public void setEnabled(boolean enabled){
        if(enabled){
            logic.doUpdate = false;
            Timers.runTask(2f, () -> {
                impl.start(this::runLogic);
                this.enabled = true;
            });
        }else{
            this.enabled = false;
            impl.stop();
            Timers.runTask(2f, () -> {
                logic.doUpdate = true;
            });
        }
    }

    public boolean isEnabled(){
        return enabled;
    }

    private void runLogic(){
        try {
            while (true) {
                long time = TimeUtils.millis();
                logic.update();

                long elapsed = TimeUtils.timeSinceMillis(time);
                long target = (long) (1000 / 60f);

                delta = Math.max(elapsed, target) / 1000f * 60f;

                if (elapsed < target) {
                    impl.sleep(target - elapsed);
                }

                synchronized(updateLock) {
                    while(!rendered) {
                        updateLock.wait();
                    }
                    rendered = false;
                }
            }
        } catch (InterruptedException ex) {
            Log.info("Stopping logic thread.");
        } catch (Exception ex) {
            Gdx.app.postRunnable(() -> {
                throw new RuntimeException(ex);
            });
        }
    }

    public interface ThreadProvider {
        boolean isOnThread();
        void sleep(long ms) throws InterruptedException;
        void start(Runnable run);
        void stop();
    }
}
