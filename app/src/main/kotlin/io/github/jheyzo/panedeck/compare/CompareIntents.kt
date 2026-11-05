package io.github.jheyzo.panedeck.compare

import android.content.Intent
import android.net.Uri
import org.json.JSONObject

/**
 * Compare search entries: one product name, searched in several apps at
 * once, each in its own window. No price scraping, no ordering.
 *
 * Adapted from Panes Workbench 3.0.5 (Apache License 2.0, yishisanren),
 * via the zealler9560 mirror, with attribution retained in DESIGN_COMPARE.md.
 * The URIs below are the shopping/food apps' own public search entry
 * points, not Panes code; they must be re-verified on device, because
 * app deep-link schemes change between app versions. A target whose
 * intent does not resolve on this device is skipped by the caller.
 *
 * Each shopping target also carries an https fallback (same search, via
 * the app's public web search URL, package-pinned to the same app).
 * It is only used when the custom scheme does not resolve or its launch
 * fails. The https URLs were checked against public references on
 * 2026-11-05: Taobao s.taobao.com/search?q=, JD
 * search.jd.com/Search?keyword=&enc=utf-8, Pinduoduo
 * mobile.yangkeduo.com/search_result.html?search_key=, Douyin
 * www.douyin.com/search/{query}?type=general (general search, not
 * mall-only: the user may need to tap the 商品 tab). Delivery targets
 * have no verifiable https search URL, so they have no https fallback;
 * Meituan keeps its standalone takeout-app custom-scheme fallback in
 * CompareRunner. An https fallback that lands in a browser may hit the
 * site's login wall (known for Taobao and Pinduoduo web search); in-app
 * App-Link handling is the expected path, not the browser.
 */
internal object CompareIntents {
    enum class Mode { SHOPPING, DELIVERY }

    data class Target(val name: String, val intent: Intent, val httpsFallback: Intent? = null)

    fun searches(mode: Mode, rawQuery: String): List<Target> {
        val query = rawQuery.trim()
        require(query.isNotEmpty() && query.length <= 200 && query.none { it.isISOControl() })
        val encoded = Uri.encode(query)
        fun jd(instant: Boolean): Intent {
            val params = if (instant) JSONObject().put("category", "jump").put("des", "channelProductList")
                .put("from", "channelProductList").put("caller", "3000").put("svcType", "lbs")
                .put("channelId", "13376").put("keyword", query)
                .put("extParams", JSONObject().put("hourlyGoChannelStyle", "1"))
            else JSONObject().put("category", "jump").put("des", "productList")
                .put("from", "search").put("keyWord", encoded)
            return view("openapp.jdmobile://virtual?params=${Uri.encode(params.toString())}", "com.jingdong.app.mall")
        }
        return if (mode == Mode.DELIVERY) listOf(
            Target("京东外卖", jd(true)),
            Target("淘宝闪购", view("taobao://tb.ele.me/app/eleme/et-search/result?keyword=$encoded", "com.taobao.taobao")),
            Target("美团外卖", view("imeituan://www.meituan.com/takeout/poi/search?query=$encoded", "com.sankuai.meituan")),
        ) else listOf(
            Target(
                "拼多多",
                view("pinduoduo://com.xunmeng.pinduoduo/search_result.html?search_key=$encoded", "com.xunmeng.pinduoduo"),
                https("https://mobile.yangkeduo.com/search_result.html?search_key=$encoded", "com.xunmeng.pinduoduo"),
            ),
            Target(
                "抖音商城",
                view("snssdk1128://search?keyword=$encoded&search_style=commodity_center&hideMiddlePage=1", "com.ss.android.ugc.aweme"),
                https("https://www.douyin.com/search/$encoded?type=general", "com.ss.android.ugc.aweme"),
            ),
            Target(
                "京东",
                jd(false),
                https("https://search.jd.com/Search?keyword=$encoded&enc=utf-8", "com.jingdong.app.mall"),
            ),
            Target(
                "淘宝",
                view("taobao://s.taobao.com/search?q=$encoded", "com.taobao.taobao"),
                https("https://s.taobao.com/search?q=$encoded", "com.taobao.taobao"),
            ),
        )
    }

    private fun view(uri: String, target: String) = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        .setPackage(target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun https(uri: String, target: String) = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        .setPackage(target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
