package com.ttech.invoicemaker.data

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * HTMLを WebView に読み込み、Android 標準の印刷ダイアログを開く(「PDFとして保存」も選べる)。
 * Webアプリ版の window.print() に相当する。
 *
 * 印刷が終わるまで WebView が破棄されないよう、呼び出し側から参照を保持できるように返す。
 */
fun printHtml(context: Context, html: String, jobName: String): WebView {
    val webView = WebView(context)
    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String?) {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            printManager.print(
                jobName,
                view.createPrintDocumentAdapter(jobName),
                PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build(),
            )
        }
    }
    webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    return webView
}
