<template>
  <div class="w-full h-full" style="padding:16px;">
    <div style="display:flex;flex-wrap:wrap;gap:8px;align-items:center;margin-bottom:12px;">
      <strong>CocoPoseEditor (Vue 2)</strong>
      <button @click="onRotate(-15)">Rotate -15°</button>
      <button @click="onRotate(15)">Rotate +15°</button>
      <button @click="onScale(0.9)">Scale 90%</button>
      <button @click="onScale(1.1)">Scale 110%</button>
      <button @click="onMirror">Mirror LR</button>
      <label style="margin-left:8px;">
        <input type="checkbox" v-model="ikEnabled" />
        2-bone IK (wrists/ankles)
      </label>

      <button @click="onReset" style="margin-left:8px;">Reset</button>
      <button @click="onSavePose" style="margin-left:4px;">Save pose</button>
      <button @click="onExport" style="margin-left:8px;">Export JSON</button>

      <!-- ★ ここが今回追加する検索ボタン -->
      <button @click="onSearch" style="margin-left:8px;">Search Similar Cuts</button>

      <label style="margin-left:8px;cursor:pointer;">
        Import JSON
        <input type="file" accept="application/json" style="display:none" @change="onImport">
      </label>
      <label style="margin-left:8px;cursor:pointer;">
        Background
        <input type="file" accept="image/*" style="display:none" @change="onBgUpload">
      </label>
    </div>

    <div style="border:1px solid #ddd; border-radius:12px; overflow:hidden;">
      <canvas ref="canvas" :width="W" :height="H"
              @mousedown="onMouseDown"
              @mousemove="onMouseMove"
              @mouseup="onMouseUp"
              @mouseleave="onMouseUp"></canvas>
    </div>

    <p style="color:#555;margin-top:10px;">
      使い方：青い点（関節）をドラッグで配置。グレーは保存済みのポーズ。
      手首/足首をドラッグすると、肘/膝が長さを保って追従（2-bone IK）。
      Mirror/Rotate/Scale/Reset、背景画像、JSON入出力、ポーズ保存、検索に対応。
    </p>
  </div>
</template>

<script>
export default {
  name: 'CocoPoseEditor',
  data() {
    const W = 1200, H = 800;
    return {
      W, H,
      ctx: null,
      points: initPose(W, H),     // 現在編集中の17点
      savedPoses: [],             // 過去に Save されたポーズ一覧（履歴）
      currentVersion: -1,         // 今どのバージョンを見ているか（-1 は「未保存の最新」）
      dragIdx: null,
      ikEnabled: true,
      bgImg: null,
    };
  },
  mounted() {
    const c = this.$refs.canvas.getContext('2d');
    this.ctx = c;
    this.draw();
  },
  beforeDestroy() {
    window.removeEventListener('mousemove', this.onMouseMove);
    window.removeEventListener('mouseup', this.onMouseUp);
  },
  watch: {
    points: {
      deep: true,
      handler() { this.draw(); }
    },
    bgImg() { this.draw(); }
  },
  computed: {
    center() {
      const L = this.points[11], R = this.points[12];
      return [(L[0] + R[0]) * 0.5, (L[1] + R[1]) * 0.5];
    }
  },
  methods: {
    // ===== 描画 =====
    draw() {
      if (!this.ctx) return;
      const c = this.ctx, { W, H } = this;
      c.clearRect(0,0,W,H);
      c.fillStyle = '#fff';
      c.fillRect(0,0,W,H);
      if (this.bgImg) c.drawImage(this.bgImg, 0, 0, W, H);

      // 保存済みポーズ（グレー）を描画
      this.savedPoses.forEach(pose => {
        const pts = pose.points;
        // 骨
        c.lineWidth = 2;
        c.strokeStyle = 'rgba(107,114,128,0.5)'; // gray-500, 半透明
        EDGES.forEach(([a,b]) => {
          const pa = pts[a], pb = pts[b];
          if (!pa || !pb) return;
          c.beginPath(); c.moveTo(pa[0], pa[1]); c.lineTo(pb[0], pb[1]); c.stroke();
        });
        // 点
        pts.forEach(p => {
          c.beginPath();
          c.arc(p[0], p[1], 5, 0, Math.PI*2);
          c.fillStyle = 'rgba(156,163,175,0.9)'; // gray-400
          c.fill();
        });
      });

      // 現在編集中のポーズ（青）
      c.lineWidth = 3;
      c.strokeStyle = '#1f2937';
      EDGES.forEach(([a,b]) => {
        const pa = this.points[a], pb = this.points[b];
        if (!pa || !pb) return;
        c.beginPath(); c.moveTo(pa[0], pa[1]); c.lineTo(pb[0], pb[1]); c.stroke();
      });

      this.points.forEach((p,i) => {
        c.beginPath(); c.arc(p[0], p[1], 7, 0, Math.PI*2);
        c.fillStyle = (i === this.dragIdx) ? '#f59e0b' : '#2563eb';
        c.fill();
        c.font = '12px Menlo, ui-monospace';
        c.fillStyle = '#111827';
        c.fillText(`${i}:${KP_NAMES[i]}`, p[0] + 10, p[1] - 10);
      });
    },

    // ===== マウス操作 =====
    pick(x,y) {
      for (let i=this.points.length-1; i>=0; i--) {
        const p = this.points[i];
        if (Math.hypot(p[0]-x, p[1]-y) <= 10) return i;
      }
      return null;
    },
    toCanvasXY(e) {
      const rect = this.$refs.canvas.getBoundingClientRect();
      const sx = this.W / rect.width;
      const sy = this.H / rect.height;
      const x = (e.clientX - rect.left) * sx;
      const y = (e.clientY - rect.top)  * sy;
      return [x, y];
    },
    onMouseDown(e) {
      const [x, y] = this.toCanvasXY(e);
      this.dragIdx = this.pick(x,y);
      if (this.dragIdx !== null) {
        window.addEventListener('mousemove', this.onMouseMove);
        window.addEventListener('mouseup', this.onMouseUp);
        this.onMouseMove(e);
      }
    },
    onMouseMove(e) {
      if (this.dragIdx === null) return;
      const [x, y] = this.toCanvasXY(e);
      const P = this.points.map(p => [p[0], p[1]]);
      const idx = this.dragIdx;

      const isLW = idx===9,  isRW = idx===10;
      const isLA = idx===15, isRA = idx===16;

      const setDirect = () => { P[idx] = [x,y]; };

      if (this.ikEnabled && (isLW || isRW || isLA || isRA)) {
        if (isLW) {
          const { newJoint, newEnd } = solveTwoBone(P[5], P[7], P[9], [x,y]);
          P[7]=newJoint; P[9]=newEnd;
        } else if (isRW) {
          const { newJoint, newEnd } = solveTwoBone(P[6], P[8], P[10], [x,y]);
          P[8]=newJoint; P[10]=newEnd;
        } else if (isLA) {
          const { newJoint, newEnd } = solveTwoBone(P[11], P[13], P[15], [x,y]);
          P[13]=newJoint; P[15]=newEnd;
        } else if (isRA) {
          const { newJoint, newEnd } = solveTwoBone(P[12], P[14], P[16], [x,y]);
          P[14]=newJoint; P[16]=newEnd;
        }
      } else {
        setDirect();
      }
      this.points = P;
    },
    onMouseUp() {
      this.dragIdx = null;
      window.removeEventListener('mousemove', this.onMouseMove);
      window.removeEventListener('mouseup', this.onMouseUp);
    },

    // ===== 変形 =====
    transformAll(fn) { this.points = this.points.map(fn); },
    onMirror() {
      const cx = this.center[0];
      this.transformAll(p => [2*cx - p[0], p[1]]);
      const swap = (a,b) => { const t=this.points[a]; this.points[a]=this.points[b]; this.points[b]=t; };
      [[1,2],[3,4],[5,6],[7,8],[9,10],[11,12],[13,14],[15,16]].forEach(([a,b]) => swap(a,b));
      this.draw();
    },
    onRotate(deg) {
      const th = deg * Math.PI / 180;
      const c = Math.cos(th), s = Math.sin(th);
      const ctr = this.center;
      this.transformAll(p => {
        const v = [p[0]-ctr[0], p[1]-ctr[1]];
        const r = [v[0]*c - v[1]*s, v[0]*s + v[1]*c];
        return [ctr[0]+r[0], ctr[1]+r[1]];
      });
      this.draw();
    },
    onScale(sf) {
      const ctr = this.center;
      this.transformAll(p => [ctr[0] + (p[0]-ctr[0])*sf, ctr[1] + (p[1]-ctr[1])*sf]);
      this.draw();
    },

    // ===== Reset / Save pose バージョン管理 =====
    onReset() {
      if (this.currentVersion === -1) {
        // まだこのバージョンは Save されていないので、単に初期姿勢に戻す
        this.points = initPose(this.W, this.H);
      } else {
        // すでに保存済みのバージョンを見ている状態
        // もし points が保存時から変更されていれば、その保存済みに戻す
        const saved = this.savedPoses[this.currentVersion];
        if (!saved) return;
        this.points = saved.points.map(p => [p[0], p[1]]);
        // その状態でさらに Reset を押された場合、
        // 「ひとつ前のバージョン」に戻す挙動
        this.currentVersion = Math.max(-1, this.currentVersion - 1);
        if (this.currentVersion >= 0) {
          const prev = this.savedPoses[this.currentVersion];
          if (prev) {
            this.points = prev.points.map(p => [p[0], p[1]]);
          }
        } else {
          this.points = initPose(this.W, this.H);
        }
      }
    },

    onSavePose() {
      const snapshot = this.points.map(p => [p[0], p[1]]);
      this.savedPoses.push({ points: snapshot });
      this.currentVersion = this.savedPoses.length - 1;
      // Save したあと、現在の編集用ポーズを「保存済みと同じ初期状態」にする
      this.points = snapshot.map(p => [p[0], p[1]]);
      this.draw();
    },

    // ===== 検索ボタン（今回追加）=====
    onSearch() {
      // 現在の17点を、そのまま or 正規化して親コンポーネントに投げる
      // ここではキャンバス座標のまま送る。正規化はバックエンドでしてもOK。
      const payload = {
        keypoints: this.points.map((p, i) => ({
          id: i,
          name: KP_NAMES[i],
          x: p[0],
          y: p[1],
        })),
        width: this.W,
        height: this.H,
      };
      this.$emit('search', payload);
    },

    // ===== I/O =====
    onExport() {
      const out = {
        keypoints: this.points.map((p,i) => ({ id:i, name:KP_NAMES[i], x:p[0], y:p[1], v:2 })),
        width: this.W, height: this.H,
      };
      const blob = new Blob([JSON.stringify(out,null,2)], { type:'application/json' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url; a.download = 'coco17_query.json'; a.click();
      URL.revokeObjectURL(url);
    },
    onImport(e) {
      const file = e.target.files && e.target.files[0];
      if (!file) return;
      const fr = new FileReader();
      fr.onload = () => {
        try {
          const j = JSON.parse(String(fr.result));
          const persons = Array.isArray(j.persons) ? j.persons : [];
          if (!persons.length) throw new Error('persons array is empty');
          const pickPerson = (arr) => {
            let best = null, bestScore = -Infinity;
            arr.forEach(p => {
              const sc = Array.isArray(p.keypoint_scores) && p.keypoint_scores.length
                ? p.keypoint_scores.reduce((a,b)=>a+Number(b||0),0) / p.keypoint_scores.length
                : 0;
              if (sc > bestScore) { bestScore = sc; best = p; }
            });
            return best || arr[0];
          };
          const person = pickPerson(persons);
          const rawKps = Array.isArray(person.keypoints) ? person.keypoints : [];
          let pts = rawKps.map(v => [Number(v[0])||0, Number(v[1])||0]).slice(0,17);
          while (pts.length < 17) {
            pts.push(pts[pts.length-1] ? [...pts[pts.length-1]] : [0,0]);
          }
          const fitted = fitPointsToCanvas(pts, this.W, this.H, 40);
          this.points = fitted;
        } catch (err) {
          console.warn('Invalid Import JSON:', err);
          alert('Import 失敗：JSON 形式を確認してください。');
        }
      };
      fr.readAsText(file);
    },
    onBgUpload(e) {
      const file = e.target.files && e.target.files[0];
      if (!file) return;
      const url = URL.createObjectURL(file);
      const img = new Image();
      img.onload = () => { this.bgImg = img; this.draw(); };
      img.src = url;
    },
  }
};

// ===== 定数 / ヘルパ =====
const KP_NAMES = [
  'nose', 'left_eye','right_eye', 'left_ear','right_ear',
  'left_shoulder','right_shoulder','left_elbow','right_elbow',
  'left_wrist','right_wrist','left_hip','right_hip',
  'left_knee','right_knee','left_ankle','right_ankle'
];

const EDGES = [
  [5,7],[7,9], [6,8],[8,10],
  [11,13],[13,15], [12,14],[14,16],
  [5,6], [11,12], [5,11],[6,12],
  [0,1],[0,2],[1,3],[2,4], [0,5],[0,6],
];

function initPose(W,H){
  const cx=W*0.35, cy=H*0.35, s=Math.min(W,H)*0.15;
  const pts = Array(17).fill(0).map(()=>[cx,cy]);
  pts[11]=[cx-s*0.25,cy+s*0.7]; pts[12]=[cx+s*0.25,cy+s*0.7];
  pts[5]=[cx-s*0.35,cy+s*0.2];  pts[6]=[cx+s*0.35,cy+s*0.2];
  pts[7]=[cx-s*0.7, cy+s*0.25]; pts[8]=[cx+s*0.7, cy+s*0.25];
  pts[9]=[cx-s,     cy+s*0.3];  pts[10]=[cx+s,     cy+s*0.3];
  pts[13]=[cx-s*0.2, cy+s*1.2]; pts[14]=[cx+s*0.2, cy+s*1.2];
  pts[15]=[cx-s*0.18,cy+s*1.7]; pts[16]=[cx+s*0.18,cy+s*1.7];
  pts[0]=[cx, cy-s*0.2];
  pts[1]=[cx-s*0.08, cy-s*0.22]; pts[2]=[cx+s*0.08, cy-s*0.22];
  pts[3]=[cx-s*0.14, cy-s*0.18]; pts[4]=[cx+s*0.14, cy-s*0.18];
  return pts;
}

// 2ボーンIK
function solveTwoBone(root, joint, end, target){
  const sub=(a,b)=>[a[0]-b[0], a[1]-b[1]];
  const add=(a,b)=>[a[0]+b[0], a[1]+b[1]];
  const mul=(a,s)=>[a[0]*s, a[1]*s];
  const len=a=>Math.hypot(a[0], a[1]);
  const norm=a=>{const L=len(a); return L>1e-8?[a[0]/L,a[1]/L]:[0,0];};
  const clamp=(x,lo,hi)=>Math.min(Math.max(x,lo),hi);

  const r=root, v1=sub(joint,root);
  const L1=len(v1);
  const L2=len(sub(end,joint));
  const rt=sub(target,r);
  const d=Math.max(1e-6, Math.min(len(rt), L1+L2-1e-6));
  const u=norm(rt);
  const cosA=clamp((L1*L1 + d*d - L2*L2)/(2*L1*d), -1, 1);
  const sinA=Math.sqrt(Math.max(0,1-cosA*cosA));
  const crossSign = Math.sign(u[0]*v1[1]-u[1]*v1[0]) || 1;
  const ortho=[-u[1], u[0]];
  const newJoint = add(r, add(mul(u, L1*cosA), mul(ortho, L1*sinA*crossSign)));
  const newEnd   = add(newJoint, mul(norm(sub(target, newJoint)), L2));
  return { newJoint, newEnd };
}

// 点群をキャンバスにフィット
function fitPointsToCanvas(pts, W, H, margin=40) {
  let minX=Infinity, minY=Infinity, maxX=-Infinity, maxY=-Infinity;
  pts.forEach(([x,y]) => {
    if (Number.isFinite(x) && Number.isFinite(y)) {
      if (x<minX) minX=x; if (y<minY) minY=y;
      if (x>maxX) maxX=x; if (y>maxY) maxY=y;
    }
  });
  if (!isFinite(minX) || !isFinite(minY) || !isFinite(maxX) || !isFinite(maxY)) {
    return initPose(W, H);
  }
  const bw = Math.max(1, maxX - minX);
  const bh = Math.max(1, maxY - minY);
  const sx = (W - 2*margin) / bw;
  const sy = (H - 2*margin) / bh;
  const s = Math.min(sx, sy);
  const cxCanvas = W/2, cyCanvas = H/2;
  const cxData = (minX + maxX) / 2;
  const cyData = (minY + maxY) / 2;

  return pts.map(([x,y]) => {
    const nx = (x - cxData) * s + cxCanvas;
    const ny = (y - cyData) * s + cyCanvas;
    return [nx, ny];
  });
}
</script>

<style scoped>
button {
  padding: 6px 10px;
  border:1px solid #ddd;
  border-radius: 999px;
  background: #fff;
  cursor: pointer;
}
button:active { transform: scale(0.98); }
canvas { display:block; width:100%; height:auto; }
</style>
