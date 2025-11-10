# proto_action_recognizer.py
# 17点スケルトン(T, 17, 2) から簡易な行動推定を行うプロトタイプ版
#
# 使い方(ざっくり):
#  1. プロトタイプ用の骨格列を用意 (実データ or 手書き)
#  2. build_prototypes() で特徴量辞書を作る
#  3. classify_action() にクエリ骨格列を渡して行動を推定する

import numpy as np
from typing import Dict, List, Tuple
import os
import json
import glob

# ======== スケルトンの仕様 ========
# joint index (COCO 17):
# 0: nose
# 1: left_eye, 2: right_eye
# 3: left_ear, 4: right_ear
# 5: left_shoulder, 6: right_shoulder
# 7: left_elbow,   8: right_elbow
# 9: left_wrist,   10: right_wrist
# 11: left_hip,    12: right_hip
# 13: left_knee,   14: right_knee
# 15: left_ankle,  16: right_ankle

LEFT_HIP  = 11
RIGHT_HIP = 12
LEFT_SHOULDER  = 5
RIGHT_SHOULDER = 6
LEFT_ANKLE = 15
RIGHT_ANKLE = 16


# ========= ユーティリティ =========

def _nan_to_num(seq: np.ndarray) -> np.ndarray:
    """NaN が混じっていたら簡単に補完する（前値保持→0埋め）"""
    x = seq.copy()
    # shape: (T, J, 2)
    T, J, C = x.shape
    for j in range(J):
        for c in range(C):
            col = x[:, j, c]
            # bool mask が全部 NaN の可能性もあるので注意
            if np.all(np.isnan(col)):
                x[:, j, c] = 0.0
            else:
                # 前値保持による簡易補間
                valid = ~np.isnan(col)
                first_valid = np.argmax(valid)
                col[:first_valid] = col[first_valid]
                for t in range(first_valid + 1, T):
                    if np.isnan(col[t]):
                        col[t] = col[t - 1]
                x[:, j, c] = col
    return x


# すでに numpy などは import 済みのはず

def load_mmpose_sequence_from_dir(dir_path: str) -> np.ndarray:
    """
    mmpose の JSON 群が入っているディレクトリから、
    (T, 17, 2) 形状のスケルトン時系列を作る。

    想定JSON形式:
    {
      "image_path": "...",
      "meta": {...},
      "persons": [
        {
          "bbox": [[x1,y1,x2,y2]],
          "keypoints": [[x,y], ... (17個想定)],
          "keypoint_scores": [score1, ..., score17]
        },
        ...
      ]
    }

    - ファイル名でソートした順にフレーム順とみなす
    - persons が複数いる場合は、
      平均 keypoint_scores が最大の人物を採用
    - keypoints が 17 個未満なら最後の点を複製して 17 個に埋める
    """
    # *.json を名前順に
    json_paths = sorted(glob.glob(os.path.join(dir_path, "*.json")))
    if not json_paths:
        raise ValueError(f"No JSON files found in directory: {dir_path}")

    frames = []

    for jp in json_paths:
        with open(jp, "r", encoding="utf-8") as f:
            data = json.load(f)

        persons = data.get("persons", [])
        if not persons:
            # このフレームに人物が検出されていなければスキップ
            continue

        # persons の中から「平均 keypoint_scores が最大」の人物を選ぶ
        best_person = None
        best_score = -1.0
        for p in persons:
            kps = p.get("keypoints", [])
            scores = p.get("keypoint_scores", [])
            if not kps:
                continue
            # スコアは無ければ 0 埋め扱い
            if scores:
                s = float(sum(float(v) for v in scores) / len(scores))
            else:
                s = 0.0
            if s > best_score:
                best_score = s
                best_person = p

        if best_person is None:
            continue

        keypoints = best_person.get("keypoints", [])
        if not keypoints:
            continue

        # numpy 配列 (N,2) に変換
        kps_arr = np.asarray(keypoints, dtype=np.float32)  # shape: (N, 2)

        # 17 点に揃える（多ければ切る、足りなければ最後を複製）
        if kps_arr.shape[0] > 17:
            kps_arr = kps_arr[:17, :]
        elif kps_arr.shape[0] < 17:
            if kps_arr.shape[0] == 0:
                # さすがにここには来ない想定だが念のため
                kps_arr = np.zeros((17, 2), dtype=np.float32)
            else:
                pad = np.repeat(kps_arr[-1:, :], 17 - kps_arr.shape[0], axis=0)
                kps_arr = np.concatenate([kps_arr, pad], axis=0)

        frames.append(kps_arr)

    if not frames:
        raise ValueError(f"No valid persons/keypoints found in directory: {dir_path}")

    # (T, 17, 2)
    seq = np.stack(frames, axis=0).astype(np.float32)
    return seq





def preprocess_skeleton_sequence(seq: np.ndarray) -> np.ndarray:
    """
    入力: seq (T, 17, 2) の骨格時系列
    出力: 正規化済みスケルトン (T, 17, 2)
      - NaN 補完
      - root(腰中心)に平行移動
      - 体スケールで正規化
    """
    # 安全チェック
    seq = np.asarray(seq, dtype=np.float32)
    assert seq.ndim == 3 and seq.shape[1] == 17 and seq.shape[2] == 2, \
        f"seq shape must be (T,17,2), got {seq.shape}"

    x = _nan_to_num(seq)

    T, J, _ = x.shape

    # root 関節 = 左腰・右腰の中点
    root = (x[:, LEFT_HIP, :] + x[:, RIGHT_HIP, :]) / 2.0  # (T,2)

    # 中心を root に合わせる
    x_centered = x - root[:, None, :]  # (T, J, 2)

    # スケール: 肩～腰距離, もしくは頭頂～足首距離など
    # ここでは「両肩中点」と「両足首中点」の距離を身長の近似として使う
    shoulder_center = (x_centered[:, LEFT_SHOULDER, :] + x_centered[:, RIGHT_SHOULDER, :]) / 2.0
    ankle_center    = (x_centered[:, LEFT_ANKLE, :]    + x_centered[:, RIGHT_ANKLE, :]) / 2.0
    height_vec = ankle_center - shoulder_center  # (T, 2)
    height = np.linalg.norm(height_vec, axis=-1)  # (T,)

    # 平均身長（0除算防止）
    scale = np.mean(height)
    if scale < 1e-6:
        scale = 1.0

    x_norm = x_centered / scale
    return x_norm


def compute_motion_features(norm_seq: np.ndarray) -> np.ndarray:
    """
    正規化済みスケルトン (T,17,2) から固定長の動作特徴ベクトルを作る。
    ここでは:
      - 各関節 x,y の「位置 mean/std」
      - 各関節 x,y の「速度 mean/std」
    を concat したベクトルを採用する。

    出力: feature (D,) の1次元ベクトル
    """
    x = np.asarray(norm_seq, dtype=np.float32)
    T, J, C = x.shape
    assert C == 2

    # 位置
    pos_mean = np.mean(x, axis=0)       # (J,2)
    pos_std  = np.std(x, axis=0)        # (J,2)

    # 速度 (時間差分)
    vel = np.diff(x, axis=0)            # (T-1, J, 2)
    if T > 1:
        vel_mean = np.mean(vel, axis=0)  # (J,2)
        vel_std  = np.std(vel, axis=0)   # (J,2)
    else:
        vel_mean = np.zeros((J,2), dtype=np.float32)
        vel_std  = np.zeros((J,2), dtype=np.float32)

    # flatten: 位置 mean/std, 速度 mean/std を全部並べる
    feat = np.concatenate([
        pos_mean.flatten(),
        pos_std.flatten(),
        vel_mean.flatten(),
        vel_std.flatten()
    ], axis=0)  # shape: (J*2*4,) = 17*2*4 = 136

    # 正規化（オプション：L2）
    norm = np.linalg.norm(feat)
    if norm > 1e-8:
        feat = feat / norm
    return feat


def cosine_similarity(a: np.ndarray, b: np.ndarray) -> float:
    a = np.asarray(a, dtype=np.float32)
    b = np.asarray(b, dtype=np.float32)
    na = np.linalg.norm(a)
    nb = np.linalg.norm(b)
    if na < 1e-8 or nb < 1e-8:
        return 0.0
    return float(np.dot(a, b) / (na * nb))


# ========= プロトタイプ辞書構築 =========

def build_prototypes(
    raw_proto_sequences: Dict[str, List[np.ndarray]]
) -> Dict[str, List[np.ndarray]]:
    """
    行動ごとの「代表スケルトン時系列」から、特徴ベクトルプロトタイプを作る。

    raw_proto_sequences: {
        "run": [seq1(T,17,2), seq2(...), ...],
        "walk": [...],
        ...
    }

    戻り値: {
        "run": [feat_vec1(D,), feat_vec2(D,), ...],
        "walk": [...],
        ...
    }
    """
    proto_feats: Dict[str, List[np.ndarray]] = {}
    for action, seq_list in raw_proto_sequences.items():
        feats = []
        for seq in seq_list:
            norm_seq = preprocess_skeleton_sequence(seq)
            feat = compute_motion_features(norm_seq)
            feats.append(feat)
        proto_feats[action] = feats
    return proto_feats


# ========= 推論（分類） =========

def classify_action(
    query_seq: np.ndarray,
    prototypes: Dict[str, List[np.ndarray]]
) -> Tuple[str, Dict[str, float]]:
    """
    クエリの骨格時系列から行動クラスを推定する。

    query_seq: (T,17,2) の骨格時系列
    prototypes: build_prototypes() で作った特徴ベクトル辞書

    戻り値:
        best_action: 最もスコアの高い行動名
        scores: {action_name: score} 各行動クラスのスコア（最大類似度）
    """
    norm_seq = preprocess_skeleton_sequence(query_seq)
    q_feat = compute_motion_features(norm_seq)

    scores: Dict[str, float] = {}
    for action, feat_list in prototypes.items():
        # 行動クラス内のプロトタイプのうち、最も類似度の高いものをスコアとする
        if not feat_list:
            scores[action] = 0.0
            continue
        sims = [cosine_similarity(q_feat, f) for f in feat_list]
        scores[action] = float(max(sims))

    # 最もスコアが高い行動を返す
    best_action = max(scores.items(), key=lambda kv: kv[1])[0]
    return best_action, scores


# ========= 動作テスト用の簡易デモ =========

def _make_dummy_sequence(T: int, kind: str) -> np.ndarray:
    """
    デモ用のダミーシーケンス生成:
      kind="run" / "walk" / "idle" の3種類だけ適当につくる。
      本番では mmpose の出力（実データ）を入れてください。
    """
    # ベースとなる立ち姿(適当な形)
    base = np.zeros((17,2), dtype=np.float32)
    # 肩と腰・足をだいたいそれっぽく置く
    base[LEFT_HIP]  = np.array([-0.1, 0.0])
    base[RIGHT_HIP] = np.array([ 0.1, 0.0])
    base[LEFT_SHOULDER]  = np.array([-0.15, 0.3])
    base[RIGHT_SHOULDER] = np.array([ 0.15, 0.3])
    base[LEFT_ANKLE]  = np.array([-0.1, -0.7])
    base[RIGHT_ANKLE] = np.array([ 0.1, -0.7])

    seq = np.zeros((T, 17, 2), dtype=np.float32)

    for t in range(T):
        frame = base.copy()
        phase = 2.0 * np.pi * t / max(1, T-1)

        if kind == "run":
            # 足を大きく振る + 上下動
            offset_y = 0.1 * np.sin(2*phase)  # 上下動
            frame[:,1] += offset_y
            frame[LEFT_ANKLE, 0]  += 0.3 * np.sin(phase)
            frame[RIGHT_ANKLE, 0] += -0.3 * np.sin(phase)
        elif kind == "walk":
            # 足を少しだけ振る + 上下動も小さめ
            offset_y = 0.03 * np.sin(2*phase)
            frame[:,1] += offset_y
            frame[LEFT_ANKLE, 0]  += 0.15 * np.sin(phase)
            frame[RIGHT_ANKLE, 0] += -0.15 * np.sin(phase)
        elif kind == "idle":
            # ほぼ動かない
            frame[:,1] += 0.0
        else:
            # その他: 少しランダムに揺れるだけ
            frame += 0.02 * np.random.randn(17,2).astype(np.float32)

        seq[t] = frame

    # root/scale などは preprocessで正規化される前提なので、ここは適当でOK
    return seq


def demo():
    # 1. プロトタイプ骨格（本番では mmpose の結果などを入れる）
    proto_raw = {
        "run": [
            _make_dummy_sequence(16, "run"),
            _make_dummy_sequence(16, "run"),
        ],
        "walk": [
            _make_dummy_sequence(16, "walk"),
        ],
        "idle": [
            _make_dummy_sequence(16, "idle"),
        ]
    }

    prototypes = build_prototypes(proto_raw)

    # 2. クエリ（ここでは "run" っぽいダミーデータ）
    query_seq = _make_dummy_sequence(16, "run")
    pred, scores = classify_action(query_seq, prototypes)

    print("Predicted action:", pred)
    print("Scores:")
    for k, v in scores.items():
        print(f"  {k}: {v:.3f}")



if __name__ == "__main__":
    # スクリプトファイルと同じ階層の input_json/ を探す
    here = os.path.dirname(os.path.abspath(__file__))
    input_dir = os.path.join(here, "input_json")

    if os.path.isdir(input_dir):
        print(f"Found input_json directory: {input_dir}")
        # 1. input_json から mmpose シーケンスを読み込み
        seq = load_mmpose_sequence_from_dir(input_dir)
        print("Loaded sequence shape:", seq.shape)  # (T,17,2) のはず

        # 2. とりあえずプロトタイプはダミーで作る（本番では別途用意）
        #    ここでは「run / walk / idle」をダミー生成して比較
        proto_raw = {
            "run":  [_make_dummy_sequence(16, "run")],
            "walk": [_make_dummy_sequence(16, "walk")],
            "idle": [_make_dummy_sequence(16, "idle")],
        }
        prototypes = build_prototypes(proto_raw)

        # 3. input_json の動きを分類
        pred, scores = classify_action(seq, prototypes)
        print("Predicted action:", pred)
        print("Scores:")
        for k, v in scores.items():
            print(f"  {k}: {v:.3f}")

    else:
        # input_json がない場合は従来のダミーデモを走らせる
        print("input_json directory not found. Running internal demo() instead.")
        demo()

