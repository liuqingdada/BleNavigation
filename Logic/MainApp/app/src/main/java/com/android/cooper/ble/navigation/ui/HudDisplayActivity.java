package com.android.cooper.ble.navigation.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.amap.api.navi.AMapHudView;
import com.amap.api.navi.AMapHudViewListener;
import com.amap.api.navi.AMapNavi;
import com.amap.api.navi.AMapNaviListener;
import com.amap.api.navi.enums.NaviType;
import com.amap.api.navi.enums.PathPlanningStrategy;
import com.amap.api.navi.model.AMapCalcRouteResult;
import com.amap.api.navi.model.AMapLaneInfo;
import com.amap.api.navi.model.AMapModelCross;
import com.amap.api.navi.model.AMapNaviCameraInfo;
import com.amap.api.navi.model.AMapNaviCross;
import com.amap.api.navi.model.AMapNaviLocation;
import com.amap.api.navi.model.AMapNaviRouteNotifyData;
import com.amap.api.navi.model.AMapNaviTrafficFacilityInfo;
import com.amap.api.navi.model.AMapServiceAreaInfo;
import com.amap.api.navi.model.AimLessModeCongestionInfo;
import com.amap.api.navi.model.AimLessModeStat;
import com.amap.api.navi.model.NaviInfo;
import com.amap.api.navi.model.NaviLatLng;
import com.android.common.utils.LogUtil;
import com.android.cooper.ble.navigation.R;
import com.android.gaode.map.MapHelper;

import java.util.ArrayList;


public class HudDisplayActivity extends Activity implements AMapHudViewListener, AMapNaviListener {
    private static final String TAG = "HudDisplayActivity";

    public static void start(Context context, NaviLatLng naviStart, NaviLatLng naviEnd) {
        Intent intent = new Intent(context, HudDisplayActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("navi_start", naviStart);
        intent.putExtra("navi_end", naviEnd);
        context.startActivity(intent);
    }

    private AMapHudView mAMapHudView;
    private AMapNavi mAMapNavi;


    //起点终点
    private NaviLatLng mNaviStart = new NaviLatLng(39.989614, 116.481763);
    private NaviLatLng mNaviEnd = new NaviLatLng(39.983456, 116.3154950);
    //起点终点列表
    private ArrayList<NaviLatLng> mStartPoints = new ArrayList<NaviLatLng>();
    private ArrayList<NaviLatLng> mEndPoints = new ArrayList<NaviLatLng>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        NaviLatLng start = getIntent().getParcelableExtra("navi_start");
        if (start != null) {
            mNaviStart = start;
        }
        NaviLatLng end = getIntent().getParcelableExtra("navi_end");
        if (end != null) {
            mNaviEnd = end;
        }

        mAMapNavi = AMapNavi.getInstance(this);
        mAMapNavi.addAMapNaviListener(this);
        mAMapNavi.setEmulatorNaviSpeed(150);
        mAMapNavi.getNaviSetting().setMonitorCameraEnabled(true);
        mAMapNavi.setUseInnerVoice(true);

        setContentView(R.layout.activity_hud);
        mAMapHudView = (AMapHudView) findViewById(R.id.hudview);
        mAMapHudView.setHudViewListener(this);

        LinearLayout icons = findViewById(R.id.icons);
        for (int type : MapHelper.INSTANCE.getHUD_IMG_ACTIONS()) {
            try {
                Drawable drawable = MapHelper.INSTANCE.colHcHudDrawable(type);
                ImageView imageView = new ImageView(this);
                imageView.setImageDrawable(drawable);
                imageView.setPadding(0, 100, 0, 100);
                icons.addView(imageView);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    //-----------------HUD返回键按钮事件-----------------------
    @Override
    public void onHudViewCancel() {
        stopNavi();
        finish();
    }

    private void stopNavi() {
        mAMapNavi.stopNavi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mAMapHudView.onResume();
        mStartPoints.add(mNaviStart);
        mEndPoints.add(mNaviEnd);
        mAMapNavi.calculateDriveRoute(
                mStartPoints,
                mEndPoints,
                null,
                PathPlanningStrategy.DRIVING_DEFAULT
        );
    }

    /**
     * 返回键监听
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            stopNavi();
            finish();
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onPause() {
        super.onPause();
        mAMapHudView.onPause();

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mAMapHudView.onDestroy();
        mAMapHudView = null;
        mAMapNavi.stopNavi();
        mAMapNavi.destroy();
    }


    @Override
    public void onInitNaviFailure() {

    }

    @Override
    public void onInitNaviSuccess() {

    }

    @Override
    public void onStartNavi(int i) {

    }

    @Override
    public void onTrafficStatusUpdate() {

    }

    @Override
    public void onLocationChange(AMapNaviLocation aMapNaviLocation) {

    }

    @Override
    public void onGetNavigationText(int i, String s) {
    }

    @Override
    public void onGetNavigationText(String s) {

    }

    @Override
    public void onEndEmulatorNavi() {

    }

    @Override
    public void onArriveDestination() {

    }

    @Override
    public void onCalculateRouteFailure(int i) {

    }

    @Override
    public void onReCalculateRouteForYaw() {

    }

    @Override
    public void onReCalculateRouteForTrafficJam() {

    }

    @Override
    public void onArrivedWayPoint(int i) {

    }

    @Override
    public void onGpsOpenStatus(boolean b) {

    }

    @Override
    public void updateCameraInfo(AMapNaviCameraInfo[] aMapCameraInfos) {

    }

    @Override
    public void onServiceAreaUpdate(AMapServiceAreaInfo[] amapServiceAreaInfos) {

    }

    @Override
    public void onNaviInfoUpdate(NaviInfo naviInfo) {
        try {
            String currRoadName = naviInfo.getCurrentRoadName();
            String nextRoadName = naviInfo.getNextRoadName();
            int icon = naviInfo.getIconType();
            int currSpeed = naviInfo.getCurrentSpeed();
            int pathRetainDistance = naviInfo.getPathRetainDistance();
            int curStepRetainDistance = naviInfo.getCurStepRetainDistance();
            int curStepRetainTime = naviInfo.getCurStepRetainTime();
            int pathRetainTime = naviInfo.getPathRetainTime();
            String sb = "========== NaviInfo ==========\n" +
                    "currRoadName = " + currRoadName + "\n" +
                    "nextRoadName = " + nextRoadName + "\n" +
                    "icon = " + icon + "\n" +
                    "currSpeed = " + currSpeed + "\n" +
                    "pathRetainDistance = " + pathRetainDistance + "\n" +
                    "curStepRetainDistance = " + curStepRetainDistance + "\n" +
                    "curStepRetainTime = " + curStepRetainTime + "\n" +
                    "pathRetainTime = " + pathRetainTime + "\n" +
                    "========== NaviInfo ==========\n";
            LogUtil.d(TAG, sb);
        } catch (Throwable err) {
            LogUtil.w(TAG, "navi info: ", err);
        }
    }

    @Override
    public void OnUpdateTrafficFacility(AMapNaviTrafficFacilityInfo aMapNaviTrafficFacilityInfo) {

    }

    @Override
    public void showCross(AMapNaviCross aMapNaviCross) {

    }

    @Override
    public void hideCross() {

    }

    @Override
    public void showLaneInfo(AMapLaneInfo[] aMapLaneInfos, byte[] bytes, byte[] bytes1) {

    }

    @Override
    public void hideLaneInfo() {

    }

    @Override
    public void onCalculateRouteSuccess(int[] ints) {

    }

    @Override
    public void notifyParallelRoad(int i) {

    }

    @Override
    public void OnUpdateTrafficFacility(AMapNaviTrafficFacilityInfo[] aMapNaviTrafficFacilityInfos) {

    }

    @Override
    public void updateAimlessModeStatistics(AimLessModeStat aimLessModeStat) {

    }


    @Override
    public void updateAimlessModeCongestionInfo(AimLessModeCongestionInfo aimLessModeCongestionInfo) {

    }

    @Override
    public void onPlayRing(int i) {

    }

    @Override
    public void showModeCross(AMapModelCross aMapModelCross) {

    }

    @Override
    public void hideModeCross() {

    }

    @Override
    public void updateIntervalCameraInfo(
            AMapNaviCameraInfo aMapNaviCameraInfo,
            AMapNaviCameraInfo aMapNaviCameraInfo1,
            int i
    ) {

    }

    @Override
    public void showLaneInfo(AMapLaneInfo aMapLaneInfo) {

    }

    @Override
    public void onCalculateRouteSuccess(AMapCalcRouteResult aMapCalcRouteResult) {
        mAMapNavi.startNavi(NaviType.EMULATOR);
    }

    @Override
    public void onCalculateRouteFailure(AMapCalcRouteResult aMapCalcRouteResult) {

    }

    @Override
    public void onNaviRouteNotify(AMapNaviRouteNotifyData aMapNaviRouteNotifyData) {

    }

    @Override
    public void onGpsSignalWeak(boolean b) {

    }
}