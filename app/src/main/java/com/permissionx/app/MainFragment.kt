package com.permissionx.app

import android.Manifest
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.permissionx.app.databinding.FragmentMainBinding
import com.permissionx.guolindev.PermissionX
import com.permissionx.guolindev.PermissionxUtils

class MainFragment : Fragment() {

    private var _binding: FragmentMainBinding? = null

    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = FragmentMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
//        val context = context!!
        binding.makeRequestBtn.setOnClickListener {
            PermissionxUtils.init(this).permissions(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.SYSTEM_ALERT_WINDOW,
            ).descriptions(
                "用于发起视频通话、拍摄照片/视频、使用扫一扫",
                "用于发送语音消息",
                "监听电话状态",
                "用于通话最小化",
            ).request {
                Toast.makeText(requireContext(), "申请结果$it", Toast.LENGTH_SHORT).show()
            }
        }

        binding.makeRequestOldBtn.setOnClickListener {
            PermissionX.init(this)
                .permissions(
//                    Manifest.permission.CAMERA,
//                    Manifest.permission.ACCESS_FINE_LOCATION,
//                    Manifest.permission.RECORD_AUDIO,
//                    Manifest.permission.READ_CALENDAR,
//                    Manifest.permission.READ_CALL_LOG,
//                    Manifest.permission.READ_CONTACTS,
//                    Manifest.permission.READ_PHONE_STATE,
//                    Manifest.permission.BODY_SENSORS,
//                    Manifest.permission.ACTIVITY_RECOGNITION,
//                    Manifest.permission.SEND_SMS,
//                    Manifest.permission.READ_EXTERNAL_STORAGE,
//                    Manifest.permission.BODY_SENSORS_BACKGROUND,
                    Manifest.permission.SYSTEM_ALERT_WINDOW,
                    Manifest.permission.WRITE_SETTINGS
                )
                .setDialogTintColor(Color.parseColor("#1972e8"), Color.parseColor("#8ab6f5"))
                .onExplainRequestReason { scope, deniedList, beforeRequest ->
                    val message = "PermissionX needs following permissions to continue"
                    scope.showRequestReasonDialog(deniedList, message, "Allow", "Deny")
//                    val message = "Please allow the following permissions in settings"
//                    val dialog = CustomDialogFragment(message, deniedList)
//                    scope.showRequestReasonDialog(dialog)
                }
                .onForwardToSettings { scope, deniedList ->
                    val message = "Please allow following permissions in settings"
                    scope.showForwardToSettingsDialog(deniedList, message, "Allow", "Deny")
                }
                .request { allGranted, grantedList, deniedList ->
                    if (allGranted) {
                        Toast.makeText(activity, "All permissions are granted", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(activity, "The following permissions are denied：$deniedList", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}