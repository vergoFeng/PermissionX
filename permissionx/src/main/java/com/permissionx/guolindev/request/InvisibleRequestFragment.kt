package com.permissionx.guolindev.request

import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
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
import com.permissionx.guolindev.dialog.ForwardToSettingsDialog
import com.permissionx.guolindev.dialog.allSpecialPermissions
import com.permissionx.guolindev.dialog.permissionMapOnQ
import com.permissionx.guolindev.dialog.permissionMapOnR
import com.permissionx.guolindev.dialog.permissionMapOnS
import com.permissionx.guolindev.dialog.permissionMapOnT

class InvisibleRequestFragment : Fragment() {
    private val mHandler = Handler(Looper.getMainLooper())
    // 权限使用说明提示popup
    private var mPermissionPopup: PopupWindow? = null
    // 申请权限列表
    private var permissions: List<String>? = null
    // 申请权限时的说明内容列表
    private var descriptions: List<String>? = null
    // 申请工具类，用于回调申请结果，结束申请
    private lateinit var permissionManager: PermissionxUtils
    // 被拒绝的权限列表
    private var deniedPermissions: MutableList<String> = ArrayList()
    // 被拒绝不再提示的权限列表，包括第一次申请权限，因为shouldShowRequestPermissionRationale方法无法判断用户不操作授权时的状态
    private var deniedNotPromptPermissions: MutableList<String> = ArrayList()
    // 特殊权限列表，直接显示去设置弹窗
    private val specialPermissionSet = LinkedHashSet<String>()
    // 是否显示权限使用说明提示内容，用于控制拒绝不再提示后，不用显示使用说明内容
    private var isShowTips = true
    // 权限名称临时缓存，用于过滤多个权限在同一权限组重复展示
    private val tempSet = HashSet<String>()

    /**
     * Used to get the result when user switch back from Settings.
     */
    private val forwardToSettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            startRequest()
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

    private fun startRequest() {
        // 获取已禁止，并且可以再次提示授权的权限
        getDeniedList()
        if (specialPermissionSet.isNotEmpty()) {
            // 特殊权限直接弹出
            showForwardToSettingsDialog()
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

    private fun getDeniedList() {
        permissions?.forEach {
            if (!PermissionX.isGranted(requireActivity(), it)) {
                if (it in allSpecialPermissions) {
                    specialPermissionSet.add(it)
                } else if (ActivityCompat.shouldShowRequestPermissionRationale(
                        requireActivity(),
                        it
                    )
                ) {
                    deniedPermissions.add(it)
                } else {
                    deniedNotPromptPermissions.add(it)
                }
            }
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
    private fun showForwardToSettingsDialog() {
        AlertDialog.Builder(requireActivity())
            .setMessage(
                getString(
                    R.string.permission_tip_while_denied,
                    getAppName(),
                    getDeniedPermissionNames(specialPermissionSet.toList())
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
        PermissionX.init(this)
            .permissions(deniedPermissions + deniedNotPromptPermissions)
            .onForwardToSettings { scope, deniedList ->
                isShowTips = false
                dismissPopupWindow()
                val dialog = ForwardToSettingsDialog(
                    requireActivity(),
                    getString(
                        R.string.permission_tip_while_denied,
                        getAppName(),
                        getDeniedPermissionNames(deniedList.toList())
                    ),
                    deniedList
                )
                scope.showForwardToSettingsDialog(dialog)
            }
            .request { allGranted, _, deniedList ->
                dismissPopupWindow()
                if (allGranted) {
                    permissionManager.requestCallback?.invoke(true)
                } else {
                    showPermissionDeniedToast(deniedList)
                    permissionManager.requestCallback?.invoke(false)
                }
                mHandler.postDelayed({
                    permissionManager.removeRequestFragment()
                }, 500)
            }
    }

    private fun showTopTips() {
        if (permissions.isNullOrEmpty()) return
        descriptions?.takeIf {
            it.size == permissions!!.size
        }?.let {
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
            for (i in descriptions!!.indices) {
                getPermissionName(permissions!![i])?.let {
                    val tipView = LayoutInflater.from(requireContext())
                        .inflate(R.layout.permissionx_top_tip_layout, tipLinearLayout, false)

                    tipView.findViewById<TextView>(R.id.tv_tip_title).text = getString(
                        R.string.permission_request_top_tip_title,
                        it
                    )
                    tipView.findViewById<TextView>(R.id.tv_tip_content).text = descriptions!![i]
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
        if(sb.isNotEmpty()) sb.deleteCharAt(sb.length - 1)
        return sb.toString()
    }

    private fun getPermissionName(permission: String): String? {
        val currentVersion = Build.VERSION.SDK_INT
        val permissionGroup = when {
            currentVersion < Build.VERSION_CODES.Q -> {
                try {
                    val permissionInfo = requireContext().packageManager.getPermissionInfo(permission, 0)
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
        return if (permissionGroup != null && !tempSet.contains(permissionGroup)) {
            tempSet.add(permissionGroup)
            requireContext().getString(
                requireContext().packageManager.getPermissionGroupInfo(
                    permissionGroup,
                    0
                ).labelRes
            )
        } else {
            null
        }
    }

//    private fun getPermissionName(permission: String): String {
//        return when (permission) {
//            "android.permission.ACCESS_FINE_LOCATION" -> "定位"
//            "android.permission.READ_PHONE_STATE" -> "读取手机状态"
//            "android.permission.WRITE_EXTERNAL_STORAGE" -> "写入外部存储"
//            "android.permission.READ_EXTERNAL_STORAGE" -> "读取外部存储"
//            "android.permission.READ_MEDIA_IMAGES" -> "读取图片"
//            "android.permission.CAMERA" -> "相机"
//            "android.permission.RECORD_AUDIO" -> "录音"
//            "android.permission.READ_CONTACTS" -> "读取联系人"
//            "android.permission.WRITE_CONTACTS" -> "写入联系人"
//            "android.permission.READ_CALENDAR" -> "读取日历"
//            "android.permission.WRITE_CALENDAR" -> "写入日历"
//            "android.permission.READ_CALL_LOG" -> "读取通话记录"
//            "android.permission.WRITE_CALL_LOG" -> "写入通话记录"
//            "android.permission.READ_SMS" -> "读取短信"
//            "android.permission.SEND_SMS" -> "发送短信"
//            "android.permission.RECEIVE_SMS" -> "接收短信"
//            "android.permission.RECEIVE_MMS" -> "接收彩信"
//            "android.permission.RECEIVE_WAP_PUSH" -> "接收WAP推送"
//            "android.permission.BODY_SENSORS" -> "传感器"
//            "android.permission.SYSTEM_ALERT_WINDOW" -> "悬浮窗"
//            "android.permission.REQUEST_INSTALL_PACKAGES" -> "安装应用"
//            "android.permission.WRITE_SETTINGS" -> "修改系统设置"
//            "android.permission.ACCESS_NOTIFICATION_POLICY" -> "修改通知策略"
//            "android.permission.ACCESS_MEDIA_LOCATION" -> "访问媒体位置"
//            "android.permission.ACCESS_COARSE_LOCATION" -> "访问粗略位置"
//            "android.permission.ACCESS_BACKGROUND_LOCATION" -> "后台访问位置"
//            "android.permission.FOREGROUND_SERVICE" -> "前台服务"
//            "android.permission.USE_BIOMETRIC" -> "生物识别"
//            "android.permission.USE_FINGERPRINT" -> "指纹识别"
//            "android.permission.USE_FULL_SCREEN_INTENT" -> "全屏意图"
//            "android.permission.USE_SIP" -> "SIP"
//            "android.permission.BLUETOOTH" -> "蓝牙"
//            "android.permission.BLUETOOTH_ADMIN" -> "蓝牙管理"
//            "android.permission.BLUETOOTH_PRIVILEGED" -> "蓝牙特权"
//            "android.permission.NFC" -> "NFC"
//            "android.permission.ACCESS_NETWORK_STATE" -> "访问网络状态"
//            "android.permission.ACCESS_WIFI_STATE" -> "访问WIFI状态"
//            "android.permission.CHANGE_WIFI_STATE" -> "修改WIFI状态"
//            "android.permission.CHANGE_NETWORK_STATE" -> "修改网络状态"
//            "android.permission.CHANGE_WIFI_MULTICAST_STATE" -> "修改WIFI多播状态"
//            else -> permission
//        }
//    }

    private fun forwardToSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = Uri.fromParts("package", requireActivity().packageName, null)
        intent.data = uri
        forwardToSettingsLauncher.launch(intent)
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
}