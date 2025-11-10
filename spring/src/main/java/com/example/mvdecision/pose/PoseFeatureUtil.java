// src/main/java/com/example/mvdecision/pose/PoseFeatureUtil.java
package com.example.mvdecision.pose;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class PoseFeatureUtil {

    // COCO 17 keypoints のインデックス
    private static final int KP_LEFT_SHOULDER = 5;
    private static final int KP_RIGHT_SHOULDER = 6;
    private static final int KP_LEFT_HIP = 11;
    private static final int KP_RIGHT_HIP = 12;

    private PoseFeatureUtil() {}

    /**
     * List<List<Double>> 形式 ( [[x,y], ...] ) を 2次元配列に変換
     */
    public static double[][] toArray(List<List<Double>> list) {
        if (list == null) {
            throw new IllegalArgumentException("keypoints list is null");
        }
        double[][] pts = new double[list.size()][2];
        for (int i = 0; i < list.size(); i++) {
            List<Double> p = list.get(i);
            double x = (p != null && p.size() > 0 && p.get(0) != null) ? p.get(0) : 0.0;
            double y = (p != null && p.size() > 1 && p.get(1) != null) ? p.get(1) : 0.0;
            pts[i][0] = x;
            pts[i][1] = y;
        }
        return pts;
    }

    /**
     * 17点のスケルトンを「骨盤中心平行移動＋胴体長でスケーリング」して正規化
     *  - 原点: 左右の hip の中点
     *  - スケール: 肩の中点と hip の中点の距離（胴体長）
     */
    public static double[][] normalizeKeypoints(double[][] pts) {
        if (pts == null || pts.length < 17) {
            throw new IllegalArgumentException("Need 17 keypoints");
        }

        // 骨盤中心
        double cx = (pts[KP_LEFT_HIP][0] + pts[KP_RIGHT_HIP][0]) / 2.0;
        double cy = (pts[KP_LEFT_HIP][1] + pts[KP_RIGHT_HIP][1]) / 2.0;

        // 肩の中心
        double sx = (pts[KP_LEFT_SHOULDER][0] + pts[KP_RIGHT_SHOULDER][0]) / 2.0;
        double sy = (pts[KP_LEFT_SHOULDER][1] + pts[KP_RIGHT_SHOULDER][1]) / 2.0;

        // 胴体長
        double torso = Math.hypot(sx - cx, sy - cy);
        if (torso < 1e-6) {
            torso = 1.0;   // ゼロ割り防止
        }

        double[][] out = new double[17][2];
        for (int i = 0; i < 17; i++) {
            out[i][0] = (pts[i][0] - cx) / torso;
            out[i][1] = (pts[i][1] - cy) / torso;
        }
        return out;
    }

    /**
     * 正規化済み座標を 1 次元ベクトルにし、DB に保存しやすい文字列にする
     * 形式: "x0,y0,x1,y1,..."
     */
    public static String buildFeatureVector(double[][] normalized) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (double[] p : normalized) {
            if (!first) {
                sb.append(',');
            }
            // x, y を続けて出力
            sb.append(String.format(Locale.US, "%.6f", p[0]));
            sb.append(',');
            sb.append(String.format(Locale.US, "%.6f", p[1]));
            first = false;
        }
        return sb.toString();
    }

    /**
     * 正規化済み 2次元配列を JSON 返却用に List<List<Double>> に変換
     */
    public static List<List<Double>> toList(double[][] normalized) {
        List<List<Double>> out = new ArrayList<>();
        for (double[] p : normalized) {
            out.add(Arrays.asList(p[0], p[1]));
        }
        return out;
    }
}
