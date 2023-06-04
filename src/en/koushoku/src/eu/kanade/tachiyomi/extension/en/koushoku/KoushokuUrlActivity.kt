package eu.kanade.tachiyomi.extension.en.koushoku

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

class KoushokuUrlActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pathSegments = intent?.data?.pathSegments
        if (pathSegments != null && pathSegments.size > 1) {
            val mainIntent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                val page = pathSegments[0]
                val query = with(page) {
                    when {
                        equals("view") -> {
                            val id = pathSegments[1]
                            val key = pathSegments[2]
                            "${Koushoku.PREFIX_ID}$id/$key"
                        }
                        equals("read") -> {
                            val id = pathSegments[1]
                            val key = pathSegments[2]
                            "${Koushoku.PREFIX_ID}$id/$key"
                        }
                        else -> {
                            val subPage = pathSegments[1]
                            with(subPage) {
                                when {
                                    equals("page") -> {
                                        finish()
                                        exitProcess(0)
                                    }
                                    else -> {
                                        "${Koushoku.PREFIX_PAGE}$page/$subPage"
                                    }
                                }
                            }
                        }
                    }
                }
                putExtra("query", query)
                putExtra("filter", packageName)
            }

            try {
                startActivity(mainIntent)
            } catch (e: ActivityNotFoundException) {
                Log.e("KoushokuUrlActivity", e.toString())
            }
        } else {
            Log.e("KoushokuUrlActivity", "could not parse uri from intent $intent")
        }

        finish()
        exitProcess(0)
    }
}
