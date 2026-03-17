package com.example.assistivescroll

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

/**
 * アプリのメイン画面（エントリポイント）
 * 基本的にUIは持たず、アクセシビリティサービスが有効かどうかの判定と誘導のみを行う
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // サービスが有効かチェックし、無効なら設定画面へ誘導
        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "AssistiveScrollを使用するには、ユーザー補助設定をONにしてください", Toast.LENGTH_LONG).show()
            // OSのアクセシビリティ設定画面を直接開く
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }
    }

    /**
     * Settings.Secureから現在有効になっているアクセシビリティサービスのリストを取得し、
     * 自分自身（MyScrollService）が含まれているかチェックする
     */
    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponentName = ComponentName(this, MyScrollService::class.java)
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return enabledServices?.contains(expectedComponentName.flattenToString()) == true
    }
}