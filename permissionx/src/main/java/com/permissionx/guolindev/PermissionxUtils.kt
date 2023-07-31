package com.permissionx.guolindev

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.permissionx.guolindev.request.InvisibleRequestFragment

class PermissionxUtils {

    private var activity: FragmentActivity
    private var fragment: Fragment? = null

    constructor(activity: FragmentActivity) {
        this.activity = activity
    }

    constructor(fragment: Fragment) {
        this.fragment = fragment
        this.activity = fragment.requireActivity()
    }

    var requestCallback: ((Boolean) -> Unit)? = null
    private var permissions: List<String>? = null
    private var descriptions: List<String>? = null

    val targetSdkVersion: Int
        get() = activity.applicationInfo.targetSdkVersion

    private val fragmentManager: FragmentManager
        get() {
            return fragment?.childFragmentManager ?: activity.supportFragmentManager
        }

    private val invisibleRequestFragment: InvisibleRequestFragment
        get() {
            val existedFragment = fragmentManager.findFragmentByTag(REQUEST_FRAGMENT)
            return if (existedFragment != null) {
                existedFragment as InvisibleRequestFragment
            } else {
                val invisibleRequestFragment = InvisibleRequestFragment()
                fragmentManager.beginTransaction()
                    .add(invisibleRequestFragment, REQUEST_FRAGMENT)
                    .commitNowAllowingStateLoss()
                invisibleRequestFragment
            }
        }

    fun permissions(vararg permissions: String): PermissionxUtils {
        return permissions(listOf(*permissions))
    }

    fun permissions(permissions: List<String>): PermissionxUtils {
        this.permissions = permissions
        return this
    }

    fun descriptions(vararg descriptions: String): PermissionxUtils {
        return descriptions(listOf(*descriptions))
    }

    fun descriptions(descriptions: List<String>): PermissionxUtils {
        this.descriptions = descriptions
        return this
    }

    fun request(callback: ((Boolean) -> Unit)?) {
        requestCallback = callback
        if(permissions.isNullOrEmpty() || descriptions.isNullOrEmpty()) {
            requestCallback?.invoke(false)
        } else {
            invisibleRequestFragment.requestNow(this, permissions!!, descriptions!!)
        }
    }

    fun removeRequestFragment() {
        val existedFragment = fragmentManager.findFragmentByTag(REQUEST_FRAGMENT)
        if (existedFragment != null) {
            fragmentManager.beginTransaction().remove(existedFragment).commit()
        }
    }

    companion object {

        private const val REQUEST_FRAGMENT = "RequestFragment"

        fun init(activity: FragmentActivity): PermissionxUtils {
            return PermissionxUtils(activity)
        }

        fun init(fragment: Fragment): PermissionxUtils {
            return PermissionxUtils(fragment)
        }
    }
}