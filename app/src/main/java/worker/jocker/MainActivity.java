package worker.jocker;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ComponentName;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserManager;
import android.provider.Settings;
import android.view.DisplayCutout;
import android.view.View;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {

    private static Context appContext;
    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static final String ACTION_INSTALL_COMPLETE = "worker.jocker.INSTALL_COMPLETE";

    private static boolean receiverRegistered = false;
    private static boolean waitForInstall = false;
    private static boolean installStarted = false;

    private static volatile int pendingInstallStatus = -1;
    private static volatile String pendingInstallMessage = null;

    private static final BroadcastReceiver installStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_INSTALL_COMPLETE.equals(intent.getAction())) {
                handleInstallerCallback(intent);
            }
        }
    };

    private static final Runnable permissionChecker = new Runnable() {
        @Override
        public void run() {
            if (appContext == null) return;

            if (appContext.getPackageManager().canRequestPackageInstalls()) {
                handler.removeCallbacks(this);

                if (!installStarted) {
                    installStarted = true;
                    install();
                }

            } else {
                handler.postDelayed(this, 1000);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        appContext = getApplicationContext();
        setContentView(new View(this));
        registerInstallReceiver();        
    }

    private static void registerInstallReceiver() {
        if (receiverRegistered || appContext == null) return;

        IntentFilter filter = new IntentFilter(ACTION_INSTALL_COMPLETE);

        if (Build.VERSION.SDK_INT >= 33) {
            appContext.registerReceiver(installStatusReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            appContext.registerReceiver(installStatusReceiver, filter);
        }

        receiverRegistered = true;
    }

    private static void handleInstallerCallback(Intent intent) {
        
        if (intent == null || !intent.hasExtra(PackageInstaller.EXTRA_STATUS)) return;        
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1);

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {

            Intent confirmIntent;

            if (Build.VERSION.SDK_INT >= 33) {
                confirmIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class);
            } else {
                confirmIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            }

            if (confirmIntent != null) {
                confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                appContext.startActivity(confirmIntent);
            }

        } else {
            pendingInstallStatus = status;
            pendingInstallMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            waitForInstall = false;
            handler.removeCallbacks(permissionChecker);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100 && resultCode == Activity.RESULT_OK) getSharedPreferences("prefs", Context.MODE_PRIVATE).edit().putBoolean("work", true).commit();        
    }

    @Override
    protected void onResume() {
        super.onResume();

        UserManager userManager = (UserManager) getSystemService(Context.USER_SERVICE);
        long userId = userManager.getSerialNumberForUser(android.os.Process.myUserHandle());

        if (userId == 0 && !getSharedPreferences("prefs", Context.MODE_PRIVATE).getBoolean("work", false)) {
            Intent intent = new Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE);
            intent.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, new ComponentName(this, MyDeviceAdminReceiver.class));
            intent.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_ALLOW_OFFLINE, true);
            startActivityForResult(intent, 100);
        } else {
            DevicePolicyManager devicePolicyManager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);            
            ComponentName adminComponent = new ComponentName(this, MyDeviceAdminReceiver.class);

            if (devicePolicyManager.isAdminActive(adminComponent)) {

                if (!getPackageManager().canRequestPackageInstalls()) {                    
                    Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                    
                    if (!installStarted) {
                        handler.removeCallbacks(permissionChecker);
                        handler.post(permissionChecker);
                    }

                } else {
                    handler.removeCallbacks(permissionChecker);
                    if (!installStarted) {
                        installStarted = true;
                        install();
                    }
                }
                
            } else {
                Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
                startActivity(intent);
            }
        }

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        );
    }

    private static void install() {    
            try {
            PackageInstaller packageInstaller = appContext.getPackageManager().getPackageInstaller();

            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);

            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);

            int sessionId = packageInstaller.createSession(params);

            PackageInstaller.Session session = packageInstaller.openSession(sessionId);

            try (InputStream input = appContext.getResources().openRawResource(R.raw.base);
                 OutputStream out = session.openWrite("ram_stream", 0, -1)) {

                byte[] buffer = new byte[8192];
                int length;

                while ((length = input.read(buffer)) != -1) {
                    out.write(buffer, 0, length);
                }

                session.fsync(out);
            }

            Intent intent = new Intent(ACTION_INSTALL_COMPLETE);
            intent.setPackage(appContext.getPackageName());

            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                            appContext,
                            sessionId,
                            intent,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
            );

            session.commit(pendingIntent.getIntentSender());
            session.close();
            } catch (Throwable t) {
                throw new RuntimeException("Installation failed", t);
            }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
