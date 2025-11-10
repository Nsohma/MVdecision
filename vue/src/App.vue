<template>
  <div id="app">
    <header style="padding:12px 16px;border-bottom:1px solid #e5e7eb;display:flex;align-items:center;gap:8px;">
      <h1 style="font-size:20px;margin-right:16px;">MVdecision Pose Search</h1>
      <button
        :class="['tab-btn', currentPage==='editor' ? 'active' : '']"
        @click="currentPage='editor'"
      >
        ポーズ編集・検索
      </button>
      <button
        :class="['tab-btn', currentPage==='upload' ? 'active' : '']"
        @click="currentPage='upload'"
      >
        データセット登録
      </button>

      <div v-if="searchStatusText" style="margin-left:auto;font-size:12px;color:#6b7280;">
        {{ searchStatusText }}
      </div>
    </header>

    <main style="padding:0;">
      <coco-pose-editor
        v-if="currentPage === 'editor'"
        @search="handleSearch"
      />
      <dataset-upload v-else />
    </main>
  </div>
</template>

<script>
import CocoPoseEditor from './components/CocoPoseEditor.vue';
import DatasetUpload from './components/DatasetUpload.vue';
import axios from 'axios';

export default {
  name: 'App',
  components: {
    CocoPoseEditor,
    DatasetUpload
  },
  data() {
    return {
      currentPage: 'editor',  // 'editor' or 'upload'
      isSearching: false,
      searchStatusText: ''
    };
  },
  methods: {
    async handleSearch(payload) {
      // CocoPoseEditor から渡された 17点のスケルトン
      // payload = { keypoints: [...], width, height }

      console.log('Search pose payload:', payload);
      this.isSearching = true;
      this.searchStatusText = '検索中...';

      try {
        // ★ 後で Spring Boot 側に実装する検索API。
        //   例: POST http://localhost:8080/api/search/pose
        const res = await axios.post('/api/search/pose', payload);
        console.log('Search result:', res.data);
        this.searchStatusText = '検索完了（バックエンドのレスポンスを画面に反映予定）';

        // TODO: ここで検索結果（類似カット画像のURLなど）を別コンポーネントに流す
        //       もしくはモーダル／サイドパネルで表示する
      } catch (e) {
        console.error(e);
        this.searchStatusText = '検索エラー（バックエンド未実装の可能性）';
      } finally {
        this.isSearching = false;
      }
    }
  }
};
</script>

<style>
#app {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, 'Noto Sans JP', sans-serif;
  color: #111827;
  min-height: 100vh;
  background: #f9fafb;
}

.tab-btn {
  padding: 6px 10px;
  border-radius: 999px;
  border: 1px solid #d1d5db;
  background: #fff;
  font-size: 14px;
  cursor: pointer;
}
.tab-btn.active {
  background: #2563eb;
  border-color: #2563eb;
  color: #fff;
}
.tab-btn:active {
  transform: scale(0.98);
}
</style>
