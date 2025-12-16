package com.qeko.reader;
// 需要的 imports
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.Log;
import android.widget.TextView;

import org.mozilla.universalchardet.UniversalDetector;

import java.io.File;
import java.io.FileInputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.CoderResult;
import java.util.ArrayList;

public class PageSplitter {
    private static final String TAG = "PageSplitter";
    private static final int BLOCK_SIZE = 2048;

    private final File file;
    private final TextPaint paint;
    private int pageWidth;
    private int pageHeight;
    private Charset charset;

    public ArrayList<Long> pageOffsetList = new ArrayList<>();

    public PageSplitter(File file, TextView measureView) {
        this.file = file;
        this.paint = measureView.getPaint();
        this.pageWidth = Math.max(1, measureView.getWidth() - measureView.getPaddingLeft() - measureView.getPaddingRight());
        this.pageHeight = Math.max(1, measureView.getHeight() - measureView.getPaddingTop() - measureView.getPaddingBottom());
//        this.pageHeight = Math.max(1, measureView.getHeight() );
        this.charset = detectEncoding(file); // 保证非 null（fallback 在 detectEncoding 内已处理）
    }

    // ========== 主分页函数（改进版） ==========
    public void buildPageOffsets(float spacing) throws Exception {
        pageOffsetList.clear();
        pageOffsetList.add(0L);
        Log.d(TAG, "spacing=: "+spacing);
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long fileLength = raf.length();
            long startByte = 0;
            byte[] buffer = new byte[BLOCK_SIZE];

            while (startByte < fileLength) {
                raf.seek(startByte);
                int read = raf.read(buffer);
                if (read <= 0) break;

                // 1) 找到 buffer[0..read-1] 中最大可完整 decode 的长度（防止半字符）
                int validLen = findMaxDecodableLengthByDecoder(buffer, read, charset);
                if (validLen <= 0) { // 极少数无法 decode 的情况，推进 1 字节避免死循环
                    startByte = Math.min(fileLength, startByte + 1);
                    continue;
                }

                // 2) decode 成文本并用 StaticLayout 测试能显示多少字符
                String textBlock = new String(buffer, 0, validLen, charset);
                StaticLayout layout = StaticLayout.Builder.obtain(textBlock, 0, textBlock.length(), paint, pageWidth)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setIncludePad(false)
                        .setLineSpacing(0f, spacing)
                        .build();


                int visibleCharCount = measureFittableChars(layout);
                if (visibleCharCount <= 0) {
                    // 如果一次测不出任何可见字符，推进一个安全的、可 decode 的长度避免卡住
                    visibleCharCount = Math.min(32, textBlock.length());
                }

                // 3) 把可视字符 re-encode 成字节，然后修正边界（保证不拆分字符）
                String visibleText = textBlock.substring(0, Math.min(visibleCharCount, textBlock.length()));
                byte[] visibleBytes = visibleText.getBytes(charset);
                int proposedLen = visibleBytes.length;

                // 关键：proposedLen 是相对 startByte 的推进量，确保落在字符边界
                // 但我们要在 buffer（0..validLen-1）中找到 <= validLen 的最大 safeLen
                int safeLen = safeCutLength(buffer, Math.min(validLen, proposedLen), charset);

                // 如果 safeLen==0（非常罕见），退回使用 validLen 的 safe 切分
                if (safeLen <= 0) safeLen = safeCutLength(buffer, validLen, charset);
                if (safeLen <= 0) {
                    // 极端回退，推进 validLen（已经能 decode）
                    safeLen = validLen;
                }

                long nextStart = startByte + safeLen;

                // 保护：确保前进
                if (nextStart <= startByte) nextStart = Math.min(fileLength, startByte + validLen);

                // 到文件末尾则加入并结束
                if (nextStart >= fileLength) {
                    if (pageOffsetList.get(pageOffsetList.size()-1) != fileLength) pageOffsetList.add(fileLength);
                    break;
                }

                pageOffsetList.add(nextStart);
//                Log.d(TAG, "分页 → start=" + startByte + " next=" + nextStart + " validLen=" + validLen + " safeLen=" + safeLen);

                startByte = nextStart;
            }

            // 确保文件末尾在偏移表
            if (pageOffsetList.isEmpty() || pageOffsetList.get(pageOffsetList.size()-1) != fileLength) {
                pageOffsetList.add(fileLength);
            }
        }
    }

    // ========== 辅助：测量可放下多少字符（你已有类似函数） ==========
    private int measureFittableChars(Layout layout) {
        int lastLine = -1;
        for (int i = 0; i < layout.getLineCount(); i++) {
            if (layout.getLineBottom(i) > pageHeight) break;
            lastLine = i;
        }
        if (lastLine < 0) return 0;
        return layout.getLineEnd(lastLine);
    }

    // ========== 安全切分函数：在 buffer[0..len-1] 中返回最大合法字符边界长度 ==========
    // 支持 UTF-8 与 GBK/GB2312 专门处理，其他编码回退到 CharsetDecoder 检查
    private int safeCutLength(byte[] buffer, int len, Charset cs) {
        if (len <= 0) return 0;
        String name = cs == null ? "" : cs.name().toUpperCase();

        try {
            if (name.contains("UTF-8")) {
                return utf8SafeLen(buffer, len);
            } else if (name.contains("GBK") || name.contains("GB2312") || name.contains("GB18030")) {
                return gbkSafeLen(buffer, len);
            } else {
                // 回退：用 decoder 二分确认最大可 decode 长度
                return findMaxDecodableLengthByDecoder(buffer, len, cs);
            }
        } catch (Throwable t) {
            // 出问题则回退为 len 的最后一个小于等于 len 可解码长度
            return Math.max(0, findMaxDecodableLengthByDecoder(buffer, len, cs));
        }
    }

    // ========== UTF-8：向前回退到上一个完整字符结尾位置 ==========
    // 返回 <= len 的值，使 buffer[0..ret-1] 可完整解码为字符（不以续字节结尾）
    private int utf8SafeLen(byte[] buf, int len) {
        if (len <= 0) return 0;
        int maxCheck = Math.min(4, len); // UTF-8 最长 4 字节
        int i = len - 1;
        // 如果最后字节是一个 UTF-8 续字节（10xxxxxx），向前找到起始字节
        int contCount = 0;
        while (i >= Math.max(0, len - maxCheck)) {
            int b = buf[i] & 0xFF;
            if ((b & 0xC0) == 0x80) { // 10xxxxxx => 续字节
                contCount++;
                i--;
                continue;
            } else {
                // 找到一个起始字节或单字节
                int startByte = b;
                // 计算该 startByte 应该有多少个后续字节
                int expectedCont;
                if ((startByte & 0x80) == 0) expectedCont = 0;
                else if ((startByte & 0xE0) == 0xC0) expectedCont = 1;
                else if ((startByte & 0xF0) == 0xE0) expectedCont = 2;
                else if ((startByte & 0xF8) == 0xF0) expectedCont = 3;
                else {
                    // 非法起始字节 -> 保守回退到 i (不包含它)
                    return Math.max(0, i);
                }
                // 如果实际看到的续字节数 < expectedCont，则说明在 len 范围内字符不完整 -> 回退到 i (不包含这个 startByte)
                if (contCount < expectedCont) {
                    return Math.max(0, i);
                } else {
                    // 合法完整字符结尾
                    return len;
                }
            }
        }
        // 如果遍历完检查窗口仍然全是续字节，说明 start 在更前面（不在检查窗口），保守回退到 len - contCount - 1
        // 保守返回 len - contCount - 1（至少丢掉续字节）
        return Math.max(0, len - contCount - 1);
    }

    // ========== GBK/GB2312：若最后是半个双字节字符则回退 1 字节 ==========
    // GBK: 双字节中文的高字节范围通常 0x81-0xFE，低字节 0x40-0xFE（不全是有效但通常可用）
    private int gbkSafeLen(byte[] buf, int len) {
        if (len <= 0) return 0;
        // 如果最后一个字节是单个 ASCII（<0x80），则安全
        int last = buf[len - 1] & 0xFF;
        if (last < 0x80) return len;

        // 对 GBK 简单策略：回退直到确保不会以“高字节”单独结束
        // 检查前一个字节是否是 GBK 高字节
        if (len - 1 >= 0) {
            int prev = buf[len - 2] & 0xFF;
            // 如果 prev 是可能的 GBK 高字节（0x81..0xFE），并且 last 看起来像低字节（0x40..0xFE），则 ok
            // 否则若 prev 是高字节而我们缺失低字节（说明 len 只包含高字节） -> 回退 1
            if (prev >= 0x81 && prev <= 0xFE) {
                // prev 在高字节范围，确保 last 是低字节范围，否则说明 last 不是有效低字节 -> 仍然当做单字节处理
                if (last >= 0x40 && last <= 0xFE) {
                    return len; // 合法
                } else {
                    // 非典型低字节（极少数），回退 1
                    return len - 1;
                }
            } else {
                // prev 不是高字节 -> 当前末尾可能是单字节或已完整, 返回 len
                return len;
            }
        }
        return len;
    }

    /**
     * 使用 CharsetDecoder 二分查找最大可解码长度（供 fallback）
     */
    private int findMaxDecodableLengthByDecoder(byte[] buffer, int len, Charset charset) {
        if (len <= 0) return 0;
        try {
            CharsetDecoder decoder = charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);

            int low = 1;
            int high = len;
            int best = 0;
            while (low <= high) {
                int mid = (low + high) >>> 1;
                ByteBuffer bb = ByteBuffer.wrap(buffer, 0, mid);
                CharBuffer cb = CharBuffer.allocate(mid);
                decoder.reset();
                CoderResult cr = decoder.decode(bb, cb, true);
                if (cr.isUnderflow()) { // mid 能完整 decode
                    best = mid;
                    low = mid + 1;
                } else {
                    high = mid - 1;
                }
            }
            return best;
        } catch (Throwable t) {
            Log.w(TAG, "decoder fallback failed", t);
            return Math.max(0, Math.min(len, BLOCK_SIZE / 4));
        }
    }

    // ========== 检测文件编码（与你已有实现相兼容） ==========
    private Charset detectEncoding(File file) {
        byte[] buf = new byte[4096];
        try (FileInputStream fis = new FileInputStream(file)) {
            UniversalDetector detector = new UniversalDetector(null);
            int nread;
            while ((nread = fis.read(buf)) > 0 && !detector.isDone()) {
                detector.handleData(buf, 0, nread);
            }
            detector.dataEnd();
            String enc = detector.getDetectedCharset();
            detector.reset();
            if (enc != null) return Charset.forName(enc);
        } catch (Exception ignored) {}
        // fallback
        try { return Charset.forName("UTF-8"); } catch (Exception e) { return Charset.defaultCharset(); }
    }

    private float textSizePx = 16f;            // 默认字体 px
    private float lineSpacingMultiplier = 1.5f; // 默认行距倍数

    // ========== 新增参数设置 ==========
    public void setTextSize(float px) {
        this.textSizePx = px;
        this.paint.setTextSize(px);
    }

    public void setLineSpacingMultiplier(float multiplier) {
        this.lineSpacingMultiplier = multiplier;
    }

    public void setPageWidth(int width) {
        this.pageWidth = Math.max(1, width);
    }

    public void setPageHeight(int height) {
        this.pageHeight = Math.max(1, height);
    }
}
