package kz.yassy.taxi.ui.activity.splash;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import es.dmoral.toasty.Toasty;
import kz.yassy.taxi.BuildConfig;
import kz.yassy.taxi.MvpApplication;
import kz.yassy.taxi.R;
import kz.yassy.taxi.base.BaseActivity;
import kz.yassy.taxi.common.ConnectivityReceiver;
import kz.yassy.taxi.common.Constants;
import kz.yassy.taxi.common.LocaleHelper;
import kz.yassy.taxi.data.SharedHelper;
import kz.yassy.taxi.data.network.model.CheckVersion;
import kz.yassy.taxi.data.network.model.Service;
import kz.yassy.taxi.data.network.model.User;
import kz.yassy.taxi.ui.activity.WelcomeActivityNew;
import kz.yassy.taxi.ui.activity.main.MainActivity;

import static kz.yassy.taxi.data.SharedHelper.putKey;

public class SplashActivity extends BaseActivity implements SplashIView,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    private SplashPresenter<SplashActivity> presenter = new SplashPresenter<>();
    private GoogleApiClient mGoogleApiClient;

    @Override
    public int getLayoutId() {
        return R.layout.activity_splash;
    }

    @Override
    public void initView() {

        // Todo Miguel Verifica se o Google play server está ok para o APP
        // caso ele não esteja, e se for apenas uma atualização, exibe um dialog para o usuario atualizar
        // caso contrário, tera que informar ao usuario que o google play server é necessario para a plicação rodar
        if (!isGooglePlayServiceOk(this, this)) {
            Toast.makeText(this, "Error with Google Play Service!", Toast.LENGTH_LONG).show();
            // recomento para a plicaçãoes pois quase todos os recuros dependem do google play services
        }

        ButterKnife.bind(this);
        presenter.attachView(this);


        findViewById(R.id.button3).setOnClickListener(view -> findViewById(R.id.grplanguages).setVisibility(View.VISIBLE));

        languageReset();
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this).build();
        mGoogleApiClient.connect();

        try {
            final MessageDigest md = MessageDigest.getInstance("SHA");
            if (Build.VERSION.SDK_INT >= 28) {
                PackageInfo packageInfo = getPackageManager().getPackageInfo
                        (getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
                Signature[] signatures = packageInfo.signingInfo.getApkContentsSigners();
                for (Signature signature : signatures) {
                    md.update(signature.toByteArray());
                    Log.d("KeyHash:", new String(Base64.encode(md.digest(), Base64.DEFAULT)));
                }
            } else {
                PackageInfo info = getPackageManager().getPackageInfo
                        (BuildConfig.APPLICATION_ID, PackageManager.GET_SIGNATURES);
                for (Signature signature : info.signatures) {
                    md.update(signature.toByteArray());
                    Log.d("KeyHash:", Base64.encodeToString(md.digest(), Base64.DEFAULT));
                }
            }
        } catch (PackageManager.NameNotFoundException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }


        chooseLanguage.setOnCheckedChangeListener((group, checkedId) -> {
            showLoading();
            switch (checkedId) {
                case R.id.english:
                    language = Constants.Language.ENGLISH;
                    break;
                case R.id.arabic:
                    language = Constants.Language.ARABIC;
                    break;
            }
            presenter.changeLanguage(language);

        });
//        Intent nextScreen = new Intent(SplashActivity.this, MainActivity.class);
//        nextScreen.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
//        startActivity(nextScreen);
//        finishAffinity();
        //Log.d("FCM", "FCM Token: " + SharedHelper.getKey(baseActivity(), "device_token"));
        if (!ConnectivityReceiver.isConnected()) {
            Toasty.error(getApplicationContext(), getString(R.string.current_alternative), Toast.LENGTH_SHORT).show();
            ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    redirectionToHome();
                }

                @Override
                public void onLost(Network network) {
                    // network unavailable
                }
            };

            ConnectivityManager connectivityManager =
                    (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                connectivityManager.registerDefaultNetworkCallback(networkCallback);
            } else {
                NetworkRequest request = new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build();
                connectivityManager.registerNetworkCallback(request, networkCallback);
            }
        } else redirectionToHome();

    }

    @BindView(R.id.english)
    RadioButton english;
    @BindView(R.id.arabic)
    RadioButton arabic;

    private void languageReset() {
        String dd = LocaleHelper.getLanguage(getApplicationContext());
        switch (dd) {
            case Constants.Language.ENGLISH:
                english.setChecked(true);
                break;
            case Constants.Language.ARABIC:
                arabic.setChecked(true);
                break;
            default:
                english.setChecked(true);
                break;
        }
    }



    @BindView(R.id.choose_language)
    RadioGroup chooseLanguage;
    private void checkVersion() {
        HashMap<String, Object> map = new HashMap<>();
        map.put("version", BuildConfig.VERSION_NAME);
        map.put("device_type", BuildConfig.DEVICE_TYPE);
        map.put("sender", "user");
        presenter.checkVersion(map);
        //if (!TextUtils.isEmpty(SharedHelper.getKey(MvpApplication.getInstance(), "access_token", null)))
        //    presenter.services();
    }

    private void checkUserAppInstalled() {
//        if (isPackageExisted(SplashActivity.this))
//            showWarningAlert(getString(R.string.user_provider_app_warning));
//        else redirectionToHome();
    }

    private boolean isPackageExisted(Context context) {
        PackageManager pm = context.getPackageManager();
        try {
            pm.getPackageInfo(BuildConfig.DRIVER_PACKAGE, PackageManager.GET_META_DATA);
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
        return true;
    }

    private void showWarningAlert(String message) {
        try {
            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(SplashActivity.this);
            alertDialogBuilder
                    .setTitle(getResources().getString(R.string.warning))
                    .setMessage(message)
                    .setCancelable(false)
                    .setPositiveButton(getResources().getString(R.string.continue_app),
                            (dialog, id) -> redirectionToHome());
            AlertDialog alertDialog = alertDialogBuilder.create();
            alertDialog.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void redirectionToHome() {
        if (SharedHelper.getBoolKey(SplashActivity.this, "logged_in", false)) {
//            presenter.profile();
            startActivity(new Intent(SplashActivity.this, MainActivity.class));
            finish();
        } else {
//            Intent nextScreen = new Intent(SplashActivity.this, SharedHelper.getKey(getApplicationContext(), "showOnBoarding", true) ? WelcomeActivityNew.class : PhoneActivity.class);
            Intent nextScreen = new Intent(SplashActivity.this, WelcomeActivityNew.class);
            nextScreen.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(nextScreen);
            finishAffinity();
        }

    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mGoogleApiClient.isConnected()) mGoogleApiClient.disconnect();
    }

    @Override
    public void onSuccess(User user) {
        putKey(this, "stripe_publishable_key", user.getStripePublishableKey());
        putKey(this, "user_id", String.valueOf(user.getId()));
        putKey(this, "appContact", user.getAppContact());
        putKey(this, "currency", user.getCurrency());
        putKey(this, "lang", user.getLanguage());
        putKey(this, "walletBalance", String.valueOf(user.getWalletBalance()));
        putKey(this, "logged_in", true);
        putKey(this, "measurementType", user.getMeasurement());
        putKey(this, "referral_code", user.getReferral_unique_id());
        putKey(this, "referral_count", user.getReferral_count());
        MvpApplication.showOTP = (user.getRide_otp() != null) && (user.getRide_otp().equals("1"));

        if(findViewById(R.id.grplanguages).getVisibility()==View.VISIBLE)
        {

        }
        else

        startActivity(new Intent(SplashActivity.this, MainActivity.class));
        finish();
    }

    @Override
    public void onError(Throwable e) {
        e.printStackTrace();
        handleError(e);
    }

    @Override
    public void onSuccess(CheckVersion version) {
        try {
            if (!version.getForceUpdate()) new Handler().postDelayed(() -> {
                Log.d("Loggedin", String.valueOf(SharedHelper.getBoolKey(SplashActivity.this, "logged_in", false)));
                String device_token = String.valueOf(SharedHelper.getKey(SplashActivity.this, "device_token"));
                Log.d("device_token", device_token);
                checkUserAppInstalled();
            }, 3000);
            else showAlertDialog(version.getUrl());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private String language;
    @Override
    public void onLanguageChanged(Object object) {
        try {
            hideLoading();
        } catch (Exception e1) {
            e1.printStackTrace();
        }
        LocaleHelper.setLocale(getApplicationContext(), language);
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK |
                Intent.FLAG_ACTIVITY_NEW_TASK));
        this.overridePendingTransition(R.anim.rotate_in, R.anim.rotate_out);
    }

    private void showAlertDialog(String url) {
        AlertDialog.Builder builder = new AlertDialog.Builder(SplashActivity.this);
        builder.setTitle(getString(R.string.new_version_update));
        builder.setMessage(getString(R.string.update_version_message));
        builder.setPositiveButton(getString(R.string.update), (dialog, which) -> {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(url));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(intent);
        });
        builder.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_CHECK_SETTINGS:
                switch (resultCode) {
                    case Activity.RESULT_OK:
//                        checkVersion();
                        break;
                    case Activity.RESULT_CANCELED:
                        finish();
                        break;
                }
                break;
        }
    }

    @Override
    protected void onDestroy() {
        presenter.onDetach();
        super.onDestroy();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {

        LocationRequest mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(1000 * 10);
        mLocationRequest.setFastestInterval(5000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(mLocationRequest);
        builder.setAlwaysShow(true);

//        SettingsClient client = LocationServices.getSettingsClient(this);
//        Task<LocationSettingsResponse> task = client.checkLocationSettings(builder.build());
////        task.addOnSuccessListener(this, locationSettingsResponse -> runOnUiThread(this::checkVersion));
//
//        task.addOnFailureListener(this, e -> {
//            if (e instanceof ResolvableApiException) try {
//                ResolvableApiException resolvable = (ResolvableApiException) e;
//                resolvable.startResolutionForResult(SplashActivity.this, REQUEST_CHECK_SETTINGS);
//            } catch (IntentSender.SendIntentException sendEx) {
//                Toast.makeText(this, sendEx.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
//            }
//        });
    }

    @Override
    public void onConnectionSuspended(int i) {
        if (mGoogleApiClient != null && !mGoogleApiClient.isConnected()) mGoogleApiClient.connect();
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        if (mGoogleApiClient != null && !mGoogleApiClient.isConnected())
            mGoogleApiClient.reconnect();
    }

    @Override
    public void onSuccess(List<Service> services) {
        AsyncTask.execute(() -> {
            try {
                for (Service service : services)
                    if (!TextUtils.isEmpty(service.getMarker())) {
                        Bitmap b = getBitmapFromURL(service.getMarker());
                        if (b != null)
                            putKey(this, service.getName() + service.getId(), encodeBase64(b));
                    }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }


    private static boolean isGooglePlayServiceOk(Context context, Activity activity) {
        final int status = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context);
        if (status != ConnectionResult.SUCCESS) {
            if (GoogleApiAvailability.getInstance().isUserResolvableError(status)) {
                // o valor 300 é um codigo para o onActivityForResult
                Dialog erroDialog = GoogleApiAvailability.getInstance().getErrorDialog(activity, status, 300);
                erroDialog.setCancelable(false);
                erroDialog.show();
            }
            return false;
        }
        return true;
    }


    //W/GooglePlayServicesUtil: Google Play services out of date.  Requires 12451000 but found 11580470

}
