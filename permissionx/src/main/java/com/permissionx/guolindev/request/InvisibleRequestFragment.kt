package com.permissionx.guolindev.request

import android.Manifest
import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.permissionx.guolindev.PermissionX
import com.permissionx.guolindev.PermissionxUtils
import com.permissionx.guolindev.R
import com.permissionx.guolindev.dialog.allSpecialPermissions
import com.permissionx.guolindev.dialog.permissionMapOnQ
import com.permissionx.guolindev.dialog.permissionMapOnR
import com.permissionx.guolindev.dialog.permissionMapOnS
import com.permissionx.guolindev.dialog.permissionMapOnT

class InvisibleRequestFragment : Fragment() {
    private val mHandler = Handler(Looper.getMainLooper())

    // 权限使用说明提示popup
    private var mPermissionPopup: PopupWindow? = null

    // 申请工具类，用于回调申请结果，结束申请
    private lateinit var permissionManager: PermissionxUtils

    // 申请权限列表
    private var permissions: List<String>? = null

    // 申请权限时的说明内容列表
    private var descriptions: List<String>? = null

    // 未授予的普通权限使用说明
    private var deniedDescriptions: MutableMap<String, String> = HashMap()

    // 已同意的权限列表
    private var grantedPermissions: MutableList<String> = ArrayList()

    // 被拒绝的权限列表
    private var deniedPermissions: MutableList<String> = ArrayList()

    // 被拒绝不再提示的权限列表，包括第一次申请权限，因为shouldShowRequestPermissionRationale方法无法判断用户不操作授权时的状态
    private var deniedNotPromptPermissions: MutableList<String> = ArrayList()

    // 未授予的特殊权限列表，直接显示去设置弹窗
    private val deniedSpecialPermissions: MutableList<String> = ArrayList()

    // 是否显示权限使用说明提示内容，用于控制拒绝不再提示后，不用显示使用说明内容
    private var isShowTips = true

    // 权限名称临时缓存，用于过滤多个权限在同一权限组重复展示
    private val tempSet = HashSet<String>()

    /**
     * Used to get the result for request multiple permissions.
     */
    private val requestNormalPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grantResults ->
            postForResult {
                onRequestNormalPermissionsResult()
            }
        }

    /**
     * Used to get the result when user switch back from Settings.
     */
    private val forwardToSettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            requestAgain()
        }

    /**
     * Used to get the result for ACCESS_BACKGROUND_LOCATION permission.
     */
    private val requestBackgroundLocationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            postForResult {
                onRequestSpecialPermissionResult(RequestBackgroundLocationPermission.ACCESS_BACKGROUND_LOCATION)
            }
        }

    /**
     * Used to get the result for SYSTEM_ALERT_WINDOW permission.
     */
    private val requestSystemAlertWindowLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            postForResult {
                onRequestSpecialPermissionResult(Manifest.permission.SYSTEM_ALERT_WINDOW)
            }
        }

    /**
     * Used to get the result for WRITE_SETTINGS permission.
     */
    private val requestWriteSettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            postForResult {
                onRequestSpecialPermissionResult(Manifest.permission.WRITE_SETTINGS)
            }
        }

    /**
     * Used to get the result for MANAGE_EXTERNAL_STORAGE permission.
     */
    private val requestManageExternalStorageLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            postForResult {
                onRequestSpecialPermissionResult(RequestManageExternalStoragePermission.MANAGE_EXTERNAL_STORAGE)
            }
        }

    /**
     * Used to get the result for REQUEST_INSTALL_PACKAGES permission.
     */
    private val requestInstallPackagesLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            postForResult {
                onRequestSpecialPermissionResult(RequestInstallPackagesPermission.REQUEST_INSTALL_PACKAGES)
            }
        }

    /**
     * Used to get the result for notification permission.
     */
    private val requestNotificationLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            postForResult {
                onRequestSpecialPermissionResult(PermissionX.permission.POST_NOTIFICATIONS)
            }
        }

    /**
     * Used to get the result for BODY_SENSORS_BACKGROUND permission.
     */
    private val requestBodySensorsBackgroundLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            postForResult {
                onRequestSpecialPermissionResult(RequestBodySensorsBackgroundPermission.BODY_SENSORS_BACKGROUND)
            }
        }

    fun requestNow(
        manager: PermissionxUtils,
        permissions: List<String>,
        descriptions: List<String>
    ) {
        this.permissionManager = manager
        this.permissions = permissions
        this.descriptions = descriptions
        startRequest()
    }

    private fun requestAgain() {
        splitPermissionsList()
        permissionManager.requestCallback?.invoke(grantedPermissions.size == permissions!!.size)
        permissionManager.removeRequestFragment()
    }

    private fun startRequest() {
        // 拆分请求的权限
        splitPermissionsList()
        if (deniedSpecialPermissions.isNotEmpty()) {
            // 特殊权限分别判断是否通过，不允许弹窗提示去设置
            requestSpecialPermissions()
        } else if (deniedPermissions.isNotEmpty()) {
            // 弹出已禁止弹窗，可以再次授权
            showPermissionDeniedDialog()
        } else if (deniedNotPromptPermissions.isNotEmpty()) {
            // 权限请求
            requestPermissions()
        } else {
            // 申请的权限已同意直接回调成功
            permissionManager.requestCallback?.invoke(true)
            permissionManager.removeRequestFragment()
        }
    }

    private fun splitPermissionsList() {
        deniedDescriptions.clear()
        deniedSpecialPermissions.clear()
        deniedPermissions.clear()
        deniedNotPromptPermissions.clear()
        grantedPermissions.clear()
        permissions?.forEachIndexed { i, permission ->
            if (permission in allSpecialPermissions) {
                specicalPermissionsIsGranted(permission)
            } else if (!PermissionX.isGranted(requireActivity(), permission)) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(), permission)) {
                    deniedPermissions.add(permission)
                } else {
                    deniedNotPromptPermissions.add(permission)
                }
                descriptions?.takeIf {
                    i < it.size
                }?.let {
                    deniedDescriptions.put(permission, it[i])
                }
            } else {
                grantedPermissions.add(permission)
            }
        }
    }

    private fun specicalPermissionsIsGranted(permission: String) {
        when (permission) {
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            Manifest.permission.BODY_SENSORS_BACKGROUND -> {
                if(PermissionX.isGranted(requireActivity(), permission)) {
                    grantedPermissions.add(permission)
                } else {
                    deniedSpecialPermissions.add(permission)
                }
            }
            Manifest.permission.SYSTEM_ALERT_WINDOW -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    && permissionManager.targetSdkVersion >= Build.VERSION_CODES.M) {
                    if(Settings.canDrawOverlays(requireActivity())) {
                        grantedPermissions.add(permission)
                    } else {
                        deniedSpecialPermissions.add(permission)
                    }
                } else {
                    grantedPermissions.add(permission)
                }
            }
            Manifest.permission.WRITE_SETTINGS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    && permissionManager.targetSdkVersion >= Build.VERSION_CODES.M) {
                    if (Settings.System.canWrite(requireActivity())) {
                        grantedPermissions.add(permission)
                    } else {
                        deniedSpecialPermissions.add(permission)
                    }
                } else {
                    grantedPermissions.add(permission)
                }
            }
            Manifest.permission.MANAGE_EXTERNAL_STORAGE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                    && permissionManager.targetSdkVersion >= Build.VERSION_CODES.R) {
                    if (Environment.isExternalStorageManager()) {
                        grantedPermissions.add(permission)
                    } else {
                        deniedSpecialPermissions.add(permission)
                    }
                } else {
                    grantedPermissions.add(permission)
                }
            }
            Manifest.permission.REQUEST_INSTALL_PACKAGES -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    && permissionManager.targetSdkVersion >= Build.VERSION_CODES.O) {
                    if (requireActivity().packageManager.canRequestPackageInstalls()) {
                        grantedPermissions.add(permission)
                    } else {
                        deniedSpecialPermissions.add(permission)
                    }
                } else {
                    grantedPermissions.add(permission)
                }
            }
            Manifest.permission.POST_NOTIFICATIONS -> {
                if (PermissionX.areNotificationsEnabled(requireActivity())) {
                    grantedPermissions.add(permission)
                } else {
                    deniedSpecialPermissions.add(permission)
                }
            }
        }
    }

    private fun showRequestSpecialPermissionDialog(permission: String) {
        AlertDialog.Builder(requireActivity())
            .setMessage(
                getString(
                    R.string.permission_tip_special,
                    getAppName(),
                    getPermissionName(permission)
                )
            )
            .setPositiveButton(
                getString(R.string.permission_tip_open)
            ) { dialogInterface: DialogInterface, _: Int ->
                dialogInterface.dismiss()
                // 去设置开启
                when (permission) {
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION -> {
                        requestAccessBackgroundLocationPermissionNow()
                    }
                    Manifest.permission.SYSTEM_ALERT_WINDOW -> {
                        requestSystemAlertWindowPermissionNow()
                    }
                    Manifest.permission.WRITE_SETTINGS -> {
                        requestWriteSettingsPermissionNow()
                    }
                    Manifest.permission.MANAGE_EXTERNAL_STORAGE -> {
                        requestManageExternalStoragePermissionNow()
                    }
                    Manifest.permission.REQUEST_INSTALL_PACKAGES -> {
                        requestInstallPackagesPermissionNow()
                    }
                    Manifest.permission.POST_NOTIFICATIONS -> {
                        requestNotificationPermissionNow()
                    }
                    Manifest.permission.BODY_SENSORS_BACKGROUND -> {
                        requestBodySensorsBackgroundPermissionNow()
                    }
                }
            }
            .setCancelable(false)
            .show()
            .apply {
                getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(
                    ContextCompat.getColor(requireActivity(), R.color.permission_tip_set_now)
                )
            }
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(requireActivity())
            .setMessage(
                getString(
                    R.string.permission_tip_while_denied,
                    getAppName(),
                    getDeniedPermissionNames(deniedPermissions)
                )
            )
            .setPositiveButton(
                getString(R.string.permission_tip_grant)
            ) { dialogInterface: DialogInterface, _: Int ->
                dialogInterface.dismiss()
                // 授予
                requestPermissions()
            }
            .setNegativeButton(
                getString(R.string.permission_tip_deny)
            ) { dialogInterface: DialogInterface, _: Int ->
                dialogInterface.dismiss()
                showPermissionDeniedToast(deniedPermissions)
                permissionManager.requestCallback?.invoke(false)
                permissionManager.removeRequestFragment()
            }
            .setCancelable(false)
            .show()
            .apply {
                getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(
                    ContextCompat.getColor(requireActivity(), R.color.permission_tip_cancel)
                )
                getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(
                    ContextCompat.getColor(requireActivity(), R.color.permission_tip_set_now)
                )
            }
    }

    /**
     * 显示 去设置 弹窗
     */
    private fun showForwardToSettingsDialog(permissionsList: List<String>) {
        AlertDialog.Builder(requireActivity())
            .setMessage(
                getString(
                    R.string.permission_tip_while_denied,
                    getAppName(),
                    getDeniedPermissionNames(permissionsList)
                )
            )
            .setPositiveButton(
                getString(R.string.permission_tip_set_now)
            ) { dialogInterface: DialogInterface, _: Int ->
                dialogInterface.dismiss()
                forwardToSettings()
            }
            .setNegativeButton(
                getString(R.string.permission_tip_cancel)
            ) { dialogInterface: DialogInterface, _: Int ->
                dialogInterface.dismiss()
                permissionManager.requestCallback?.invoke(false)
                permissionManager.removeRequestFragment()
            }
            .setCancelable(false)
            .show()
            .apply {
                getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(
                    ContextCompat.getColor(requireActivity(), R.color.permission_tip_cancel)
                )
                getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(
                    ContextCompat.getColor(requireActivity(), R.color.permission_tip_set_now)
                )
            }
    }

    private fun requestPermissions() {
        showTopTips()
        requestNormalPermissionLauncher.launch((deniedPermissions + deniedNotPromptPermissions).toTypedArray())
    }

    private fun requestSpecialPermissions() {
        if(deniedSpecialPermissions.isNotEmpty()) {
            showRequestSpecialPermissionDialog(deniedSpecialPermissions[0])
        } else if (deniedPermissions.isNotEmpty()) {
            // 弹出已禁止弹窗，可以再次授权
            showPermissionDeniedDialog()
        } else if (deniedNotPromptPermissions.isNotEmpty()) {
            // 权限请求
            requestPermissions()
        } else {
            // 重新判断权限是否同意返回结果
            splitPermissionsList()
            permissionManager.requestCallback?.invoke(permissions?.size == grantedPermissions.size)
            permissionManager.removeRequestFragment()
        }
    }

    private fun requestAccessBackgroundLocationPermissionNow() {
        requestBackgroundLocationLauncher.launch(RequestBackgroundLocationPermission.ACCESS_BACKGROUND_LOCATION)
    }

    private fun requestSystemAlertWindowPermissionNow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
            && !Settings.canDrawOverlays(requireContext())
        ) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            intent.data = Uri.parse("package:${requireActivity().packageName}")
            requestSystemAlertWindowLauncher.launch(intent)
        } else {
            onRequestSpecialPermissionResult(Manifest.permission.SYSTEM_ALERT_WINDOW)
        }
    }

    /**
     * Request WRITE_SETTINGS permission. On Android M and above, it's request by
     * Settings.ACTION_MANAGE_WRITE_SETTINGS with Intent.
     */
    private fun requestWriteSettingsPermissionNow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(requireContext())) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
            intent.data = Uri.parse("package:${requireActivity().packageName}")
            requestWriteSettingsLauncher.launch(intent)
        } else {
            onRequestSpecialPermissionResult(Manifest.permission.WRITE_SETTINGS)
        }
    }

    /**
     * Request MANAGE_EXTERNAL_STORAGE permission. On Android R and above, it's request by
     * Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION with Intent.
     */
    @SuppressLint("QueryPermissionsNeeded")
    private fun requestManageExternalStoragePermissionNow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            var intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = Uri.parse("package:${requireActivity().packageName}")
            if (intent.resolveActivity(requireActivity().packageManager) == null) {
                intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            }
            requestManageExternalStorageLauncher.launch(intent)
        } else {
            onRequestSpecialPermissionResult(RequestManageExternalStoragePermission.MANAGE_EXTERNAL_STORAGE)
        }
    }

    /**
     * Request REQUEST_INSTALL_PACKAGES permission. On Android O and above, it's request by
     * Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES with Intent.
     */
    private fun requestInstallPackagesPermissionNow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            intent.data = Uri.parse("package:${requireActivity().packageName}")
            requestInstallPackagesLauncher.launch(intent)
        } else {
            onRequestSpecialPermissionResult(RequestInstallPackagesPermission.REQUEST_INSTALL_PACKAGES)
        }
    }

    /**
     * Request notification permission. On Android O and above, it's request by
     * Settings.ACTION_APP_NOTIFICATION_SETTINGS with Intent.
     */
    private fun requestNotificationPermissionNow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, requireActivity().packageName)
            requestNotificationLauncher.launch(intent)
        } else {
            onRequestSpecialPermissionResult(PermissionX.permission.POST_NOTIFICATIONS)
        }
    }

    /**
     * Request ACCESS_BACKGROUND_LOCATION at once by calling [Fragment.requestPermissions],
     * and handle request result in ActivityCompat.OnRequestPermissionsResultCallback.
     */
    private fun requestBodySensorsBackgroundPermissionNow() {
        requestBodySensorsBackgroundLauncher.launch(RequestBodySensorsBackgroundPermission.BODY_SENSORS_BACKGROUND)
    }

    private fun onRequestNormalPermissionsResult() {
        dismissPopupWindow()
        splitPermissionsList()
        if(permissions?.size == grantedPermissions.size) {
            permissionManager.requestCallback?.invoke(true)
            permissionManager.removeRequestFragment()
        } else if(deniedNotPromptPermissions.isNotEmpty()) {
            isShowTips = false
            showForwardToSettingsDialog(deniedNotPromptPermissions)
        } else {
            showPermissionDeniedToast(deniedPermissions)
            permissionManager.requestCallback?.invoke(false)
        }
    }

    private fun onRequestSpecialPermissionResult(specialPermission: String) {
        if (checkForGC()) {
            deniedSpecialPermissions.remove(specialPermission)
            requestSpecialPermissions()
        }
    }

    private fun forwardToSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = Uri.fromParts("package", requireActivity().packageName, null)
        intent.data = uri
        forwardToSettingsLauncher.launch(intent)
    }

    private fun showTopTips() {
        if (deniedDescriptions.isNotEmpty()) {
            mHandler.postDelayed({
                (activity?.window?.decorView as? ViewGroup)?.let { decorView ->
                    if (isShowTips) showPopupWindow(decorView)
                }
            }, 300)
        }
    }

    private fun showPopupWindow(decorView: ViewGroup) {
        if (mPermissionPopup == null) {
            val tipContentView: View = LayoutInflater.from(requireActivity())
                .inflate(R.layout.permissionx_description_popup, decorView, false)
            mPermissionPopup = PopupWindow(requireContext()).apply {
                contentView = tipContentView
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = WindowManager.LayoutParams.MATCH_PARENT
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                isTouchable = true
                isOutsideTouchable = true
            }

            tempSet.clear()
            val tipLinearLayout = tipContentView.findViewById<LinearLayout>(R.id.ll_top_tip)
            for ((permission, description) in deniedDescriptions) {
                getPermissionName(permission)?.let {
                    val tipView = LayoutInflater.from(requireContext())
                        .inflate(R.layout.permissionx_top_tip_layout, tipLinearLayout, false)

                    tipView.findViewById<TextView>(R.id.tv_tip_title).text = getString(
                        R.string.permission_request_top_tip_title,
                        it
                    )
                    tipView.findViewById<TextView>(R.id.tv_tip_content).text = description
                    tipLinearLayout.addView(tipView)
                }
            }
        }
        mPermissionPopup?.showAtLocation(decorView, Gravity.TOP, 0, 0)
    }

    private fun dismissPopupWindow() {
        if (mPermissionPopup != null && mPermissionPopup!!.isShowing) {
            mPermissionPopup!!.dismiss()
        }
    }

    private fun showPermissionDeniedToast(deniedList: List<String>) {
        Toast.makeText(
            requireActivity(), getString(
                R.string.permission_toast_while_denied,
                getAppName(),
                getDeniedPermissionNames(deniedList)
            ), Toast.LENGTH_SHORT
        ).show()
    }

    private fun getDeniedPermissionNames(deniedList: List<String>): String {
        tempSet.clear()
        val sb = StringBuilder()
        deniedList.forEach {
            getPermissionName(it)?.let { name ->
                sb.append(name)
                sb.append("、")
            }
        }
        if (sb.isNotEmpty()) sb.deleteCharAt(sb.length - 1)
        return sb.toString()
    }

    private fun getPermissionName(permission: String): String? {
        val currentVersion = Build.VERSION.SDK_INT
        val permissionGroup = when {
            currentVersion < Build.VERSION_CODES.Q -> {
                try {
                    val permissionInfo =
                        requireContext().packageManager.getPermissionInfo(permission, 0)
                    permissionInfo.group
                } catch (e: PackageManager.NameNotFoundException) {
                    e.printStackTrace()
                    null
                }
            }

            currentVersion == Build.VERSION_CODES.Q -> permissionMapOnQ[permission]
            currentVersion == Build.VERSION_CODES.R -> permissionMapOnR[permission]
            currentVersion == Build.VERSION_CODES.S -> permissionMapOnS[permission]
            currentVersion == Build.VERSION_CODES.TIRAMISU -> permissionMapOnT[permission]
            else -> {
                permissionMapOnT[permission]
            }
        }
        if ((permission in allSpecialPermissions && !tempSet.contains(permission))
            || (permissionGroup != null && !tempSet.contains(permissionGroup))
        ) {
            tempSet.add(permissionGroup ?: permission)
            return when {
                permission == Manifest.permission.ACCESS_BACKGROUND_LOCATION -> {
                    requireContext().getString(R.string.permissionx_access_background_location)
                }

                permission == Manifest.permission.SYSTEM_ALERT_WINDOW -> {
                    requireContext().getString(R.string.permissionx_system_alert_window)
                }

                permission == Manifest.permission.WRITE_SETTINGS -> {
                    requireContext().getString(R.string.permissionx_write_settings)
                }

                permission == Manifest.permission.MANAGE_EXTERNAL_STORAGE -> {
                    requireContext().getString(R.string.permissionx_manage_external_storage)
                }

                permission == Manifest.permission.REQUEST_INSTALL_PACKAGES -> {
                    requireContext().getString(R.string.permissionx_request_install_packages)
                }

                permission == Manifest.permission.POST_NOTIFICATIONS
                        && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> {
                    requireContext().getString(R.string.permissionx_post_notification)
                }

                permission == Manifest.permission.BODY_SENSORS_BACKGROUND -> {
                    requireContext().getString(R.string.permissionx_body_sensor_background)
                }

                else -> {
                    requireContext().getString(
                        requireContext().packageManager.getPermissionGroupInfo(
                            permissionGroup!!,
                            0
                        ).labelRes
                    )
                }
            }
        } else {
            return null
        }
    }

    private fun getAppName(): String {
        return try {
            val pm: PackageManager = requireContext().packageManager
            val pi = pm.getPackageInfo(requireContext().packageName, 0)
            pi?.applicationInfo?.loadLabel(pm)?.toString() ?: ""
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            ""
        }
    }

    private fun checkForGC(): Boolean {
        if (!::permissionManager.isInitialized) {
            Log.w(
                "PermissionX",
                "PermissionBuilder and ChainTask should not be null at this time, so we can do nothing in this case."
            )
            return false
        }
        return true
    }

    private fun postForResult(callback: () -> Unit) {
        mHandler.post {
            callback()
        }
    }
}