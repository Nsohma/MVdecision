<template>
  <div class="dataset-upload" style="padding:16px;">
    <h2 style="font-size:20px;margin-bottom:8px;">データセット登録（画像＋JSON zip）</h2>
    <p style="margin-bottom:12px;color:#555;">
      画像と <code>*_keypoints.json</code> がペアになったディレクトリを zip にしてアップロードします。<br>
      例: <code>A001.png</code> と <code>A001_keypoints.json</code> がセット。
    </p>

    <div style="border:1px solid #ddd;border-radius:12px;padding:16px;max-width:520px;">
      <input type="file" accept=".zip" @change="onFileChange" />
      <div v-if="selectedFile" style="margin-top:8px;">
        選択中: <strong>{{ selectedFile.name }}</strong>
      </div>

      <button
        :disabled="!selectedFile || uploading"
        @click="onUpload"
        style="margin-top:12px;"
      >
        {{ uploading ? 'アップロード中...' : 'アップロード' }}
      </button>

      <p v-if="message" :style="{marginTop:'12px', color: messageColor}">
        {{ message }}
      </p>
    </div>

    <p style="margin-top:16px;color:#777;font-size:13px;">
      ※ バックエンド側では、zip を展開して画像と JSON を読み取り、<br>
      スケルトン17点を正規化した上でデータベースに格納する処理を実装します。
    </p>
  </div>
</template>

<script>
import axios from 'axios';

export default {
  name: 'DatasetUpload',
  data() {
    return {
      selectedFile: null,
      uploading: false,
      message: '',
      messageColor: '#16a34a', // green
    };
  },
  methods: {
    onFileChange(e) {
      const file = e.target.files && e.target.files[0];
      this.selectedFile = file || null;
      this.message = '';
    },
    async onUpload() {
      if (!this.selectedFile) return;

      this.uploading = true;
      this.message = '';
      try {
        const formData = new FormData();
        formData.append('file', this.selectedFile);

        // ★ バックエンドのエンドポイント（要調整）
        //   例: POST http://localhost:8080/api/dataset/upload
        await axios.post('/api/dataset/uploadZip', formData, {
          headers: { 'Content-Type': 'multipart/form-data' }
        });

        this.message = 'アップロードに成功しました。';
        this.messageColor = '#16a34a';
        this.selectedFile = null;
      } catch (err) {
        console.error(err);
        this.message = 'アップロードに失敗しました（バックエンド未実装の可能性）。';
        this.messageColor = '#dc2626'; // red
      } finally {
        this.uploading = false;
      }
    }
  }
};
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
</style>
