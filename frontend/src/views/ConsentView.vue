<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { consentAPI, type ConsentInfo } from '@/api/client'
import { CONSENT_V1 } from '@/consents/v1'

const route = useRoute()
const token = computed(() => String(route.params.token ?? ''))

const loading = ref(true)
const submitting = ref(false)
const info = ref<ConsentInfo | null>(null)
const loadError = ref<string | null>(null)

const phoneLast4 = ref('')
const acknowledged = ref(false)
const completed = ref(false)

const canSubmit = computed(() =>
  acknowledged.value && /^\d{4}$/.test(phoneLast4.value) && !submitting.value
)

async function loadInfo() {
  loading.value = true
  loadError.value = null
  try {
    const res = await consentAPI.get(token.value)
    info.value = res.data
    if (info.value.alreadyConsented) {
      completed.value = true
    }
  } catch (e: any) {
    loadError.value = e.response?.data?.message || '유효하지 않거나 만료된 링크입니다.'
  } finally {
    loading.value = false
  }
}

async function submit() {
  if (!canSubmit.value) return
  submitting.value = true
  try {
    await consentAPI.agree(token.value, phoneLast4.value)
    completed.value = true
    ElMessage.success('동의가 완료되었습니다')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '동의 처리에 실패했습니다')
  } finally {
    submitting.value = false
  }
}

onMounted(loadInfo)
</script>

<template>
  <div class="consent-page">
    <div class="card" v-loading="loading">
      <h1>{{ CONSENT_V1.title }}</h1>

      <!-- Error / expired / already-done states -->
      <el-alert v-if="loadError" type="error" :title="loadError" :closable="false" show-icon />
      <el-alert
        v-else-if="info && info.expired && !completed"
        type="warning"
        title="동의 링크가 만료되었습니다. 학원에 재발급을 요청해 주세요."
        :closable="false"
        show-icon
      />
      <el-alert
        v-else-if="completed"
        type="success"
        title="개인정보 수집·이용에 동의해 주셔서 감사합니다. 학생이 학원 계정에 로그인할 수 있습니다."
        :closable="false"
        show-icon
      />

      <!-- Consent body -->
      <template v-if="info && !loadError && !info.expired && !completed">
        <p class="intro">{{ CONSENT_V1.intro }}</p>

        <div class="meta-box">
          <div><strong>학원</strong> {{ info.academyName || '—' }}</div>
          <div v-if="info.className"><strong>반</strong> {{ info.className }}</div>
          <div><strong>학생</strong> {{ info.studentName }}</div>
          <div><strong>보호자</strong> {{ info.parentName }} ({{ info.parentPhoneMasked }})</div>
        </div>

        <section v-for="(sec, i) in CONSENT_V1.sections" :key="i" class="section">
          <h3>{{ sec.heading }}</h3>
          <table class="kv">
            <tr v-for="(r, j) in sec.rows" :key="j">
              <th>{{ r.label }}</th>
              <td>{{ r.value }}</td>
            </tr>
          </table>
        </section>

        <div class="form">
          <el-checkbox v-model="acknowledged">
            {{ CONSENT_V1.acknowledgementLabel }}
          </el-checkbox>

          <div class="phone-row">
            <label for="phone4">본인 확인 (보호자 휴대폰 뒤 4자리)</label>
            <el-input
              id="phone4"
              v-model="phoneLast4"
              maxlength="4"
              placeholder="0000"
              style="width: 140px;"
              @input="(v: string) => (phoneLast4 = v.replace(/\D/g, '').slice(0, 4))"
            />
          </div>

          <el-button
            type="primary"
            size="large"
            :loading="submitting"
            :disabled="!canSubmit"
            @click="submit"
          >
            동의하고 제출
          </el-button>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.consent-page {
  min-height: 100vh;
  background: #f5f7fa;
  padding: 24px 16px;
}
.card {
  max-width: 640px;
  margin: 0 auto;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  padding: 28px 32px;
}
h1 {
  margin: 0 0 16px;
  font-size: 22px;
}
.intro {
  color: #606266;
  line-height: 1.6;
  margin: 12px 0 18px;
}
.meta-box {
  background: #f5f7fa;
  border-radius: 6px;
  padding: 12px 16px;
  margin-bottom: 20px;
  line-height: 1.9;
  font-size: 14px;
}
.meta-box strong {
  display: inline-block;
  width: 60px;
  color: #909399;
  font-weight: 500;
}
.section { margin: 18px 0; }
.section h3 { margin: 0 0 8px; font-size: 15px; }
.kv {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.kv th, .kv td {
  border: 1px solid #ebeef5;
  padding: 8px 12px;
  text-align: left;
  vertical-align: top;
}
.kv th {
  width: 110px;
  background: #fafafa;
  color: #606266;
  font-weight: 500;
}
.form {
  margin-top: 28px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.phone-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.phone-row label {
  font-size: 14px;
  color: #303133;
}
</style>
