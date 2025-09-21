# 『黒板太一2』ラスタ方式（ビットマップ）実装メモ

## 概要
- **方針**: アンドゥ不要前提で**全面ビットマップ**方式に統一  
- **利点**: 分割式消しゴムは**CLEAR描画**で実現でき、実装・パフォーマンスともに有利  
- **用途**: Jetpack Compose 前提。関数は**必要箇所のみコピペ**で使用可能

---

## 1. ビットマップ準備 & 背景

```kotlin
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

fun createChalkboardBitmap(width: Int, height: Int): Bitmap {
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
}

private val bgPaint = Paint().apply {
    isAntiAlias = true
    style = Paint.Style.FILL
    color = Color.rgb(11, 46, 26) // 黒板系の深緑
}

fun fillChalkboardBackground(target: Bitmap) {
    val c = Canvas(target)
    c.drawRect(0f, 0f, target.width.toFloat(), target.height.toFloat(), bgPaint)
}

fun clearAll(target: Bitmap) {
    // 全消し＝背景から塗り直す運用
    fillChalkboardBackground(target)
}
```

---

## 2. ペン描画（白/赤 × 細/太）

```kotlin
import android.graphics.Path
import androidx.compose.ui.geometry.Offset

private fun makePenPaint(color: Int, thicknessPx: Float): Paint {
    return Paint().apply {
        isAntiAlias = true
        isDither = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = thicknessPx
        this.color = color
    }
}

fun drawStroke(
    target: Bitmap,
    points: List<Offset>,
    color: Int,        // 例: Color.White.toArgb()
    thicknessPx: Float
) {
    if (points.size < 2) return
    val c = Canvas(target)
    val path = Path().apply {
        moveTo(points.first().x, points.first().y)
        for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
    }
    c.drawPath(path, makePenPaint(color, thicknessPx))
}
```

---

## 3. 黒板消し（CLEAR描画で「なぞった所だけ」消す）

```kotlin
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.BlendMode
import android.os.Build

private fun makeEraserPaint(radiusPx: Float): Paint {
    return Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = radiusPx * 2f // 半径→直径
        if (Build.VERSION.SDK_INT >= 29) {
            blendMode = BlendMode.CLEAR
        } else {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
    }
}

fun erasePath(
    target: Bitmap,
    points: List<Offset>,
    radiusPx: Float
) {
    if (points.size < 2) return
    val c = Canvas(target)
    val path = Path().apply {
        moveTo(points.first().x, points.first().y)
        for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
    }
    c.drawPath(path, makeEraserPaint(radiusPx))
}
```

> **備考**  
> - ビットマップ1枚に背景も描いている場合、消すと透明になります。黒板色を残したい場合は**二層構成**（下記）を推奨。

---

## 4. 二層構成（推奨）
- `bgBitmap`（黒板地）＋`drawBitmap`（描画レイヤ）を用意  
- 描画: `Canvas.drawBitmap(bg); Canvas.drawBitmap(draw)`  
- **消しは `drawBitmap` に対して CLEAR**（地色に影響しない）

---

## 5. サムネイル & 保存/読込

```kotlin
import java.io.File
import java.io.FileOutputStream
import android.graphics.BitmapFactory

data class SlotMeta(
    val filePath: String,
    val updatedAtMillis: Long
)

fun makeThumbnail(src: Bitmap, maxW: Int, maxH: Int): Bitmap {
    val ratio = minOf(maxW.toFloat() / src.width, maxH.toFloat() / src.height)
    val w = (src.width * ratio).toInt().coerceAtLeast(1)
    val h = (src.height * ratio).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(src, w, h, true)
}

fun savePng(target: Bitmap, file: File): SlotMeta {
    FileOutputStream(file).use { fos ->
        target.compress(Bitmap.CompressFormat.PNG, 100, fos)
    }
    return SlotMeta(file.absolutePath, System.currentTimeMillis())
}

fun loadPng(file: File): Bitmap? {
    if (!file.exists()) return null
    return BitmapFactory.decodeFile(file.absolutePath)
}
```

---

## 6. Compose 側の運用ポイント
- 画面サイズ確定後に `createChalkboardBitmap()` → `fillChalkboardBackground()`  
- **ペン**: ドラッグ点をバッファし、**指離しで `drawStroke()`**（毎フレーム直描きでもOK）  
- **消し**: ドラッグ中に**逐次 `erasePath()`**（重ければ指離しで一括）  
- 表示は `Image(bitmap = target.asImageBitmap(), …)`  
- 一覧は**サムネのみメモリ保持**、本体は**必要時ロード**（最大30スロット）

---

## 7. パフォーマンス & 品質Tips
- **点の間引き**: 直前点と距離が `radiusPx * 0.6` 未満ならスキップ → 軽くて滑らか  
- **メモリ目安**: 1080×1920/ARGB_8888 ≒ 7.9MB（常駐は編集中＋サムネ数枚）  
- **線の見た目**: `strokeCap=ROUND / strokeJoin=ROUND` と**太さの離散化**（細=6px/太=12px 等）  
- **全消し**は背景再塗りで統一（透明化より安定）

---

## 8. この方式のまとめ
- **実装最短**：分割アルゴリズム不要、消しは CLEAR で完結  
- **体感高速**：GPU任せでスムーズ  
- **要件適合**：「触ったところだけ消える」見た目を満たす  
- **制約**：線単位の再編集や高倍率の再レンダはベクタに劣る（今回は不要前提）

---

## 9. 付録：eraserPath の簡易間引き（任意）

```kotlin
import androidx.compose.ui.geometry.Offset

fun decimatePath(path: List<Offset>, minStepPx: Float): List<Offset> {
    if (path.isEmpty()) return path
    val out = ArrayList<Offset>(path.size)
    var last = path[0]
    out.add(last)
    val min2 = minStepPx * minStepPx
    for (i in 1 until path.size) {
        val p = path[i]
        val dx = p.x - last.x; val dy = p.y - last.y
        if (dx*dx + dy*dy >= min2) {
            out.add(p)
            last = p
        }
    }
    return out
}
```

> 使い方例: ドラッグ中に `eraserPath = decimatePath(eraserPath + pos, radiusPx * 0.6f)`

---

### 運用チェックリスト
- [ ] 画面確定後にビットマップ生成・背景塗り  
- [ ] ペン/消しツール切替（UIボタン）  
- [ ] ペンは指離しで描画コミット（必要なら毎フレーム描画）  
- [ ] 消しは CLEAR でなぞる（必要なら間引き）  
- [ ] PNG保存・読み込み・サムネ生成  
- [ ] 一覧30スロット（更新日時で並べ替え）  
