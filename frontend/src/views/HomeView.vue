<script setup lang="ts">
import { useRouter } from 'vue-router'
import appIconUrl from '@/assets/app-icon.svg'

const router = useRouter()

const navigateToStudents = () => {
  router.push('/students')
}

const navigateToTests = () => {
  router.push('/tests')
}
</script>

<template>
  <div style="min-height: 80vh; display: flex; align-items: center; justify-content: center">
    <div style="text-align: center; max-width: 1200px; margin: 0 auto; padding: 64px 16px">
      <!-- Hero Section -->
      <div style="margin-bottom: 48px">
        <img
          :src="appIconUrl"
          alt="킴매스"
          style="
            display: block;
            width: 144px;
            height: 144px;
            margin: 0 auto 32px;
            border-radius: 32px;
            box-shadow: 0 8px 32px rgba(6, 26, 58, 0.28);
          "
        />
        <h1
          style="
            font-size: 64px;
            font-weight: 800;
            margin-bottom: 24px;
            background: linear-gradient(135deg, #409eff, #67c23a);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
          "
        >
          학원 관리 시스템
        </h1>
        <p
          style="
            font-size: 20px;
            color: #909399;
            margin-bottom: 48px;
            max-width: 600px;
            margin-left: auto;
            margin-right: auto;
          "
        >
          효율적인 학생 관리와 시험 평가를 위한 완벽한 솔루션으로 학원 운영을 간소화하세요
        </p>
      </div>

      <!-- Feature Cards -->
      <el-row :gutter="32" style="max-width: 900px; margin: 0 auto 48px">
        <el-col :span="12">
          <el-card
            shadow="hover"
            style="height: 100%; cursor: pointer; border: none"
            body-style="padding: 48px 24px"
            @click="navigateToStudents"
          >
            <div style="text-align: center">
              <div
                style="
                  display: inline-block;
                  padding: 16px;
                  background: #409eff;
                  border-radius: 16px;
                  margin-bottom: 24px;
                "
              >
                <el-icon size="48" color="white">
                  <User />
                </el-icon>
              </div>
              <h3 style="font-size: 24px; font-weight: 600; color: #303133; margin-bottom: 12px">
                학생 관리
              </h3>
              <p style="color: #606266">
                학생 정보 등록, 수정, 조회 및 검색 기능으로 체계적인 학생 관리
              </p>
            </div>
          </el-card>
        </el-col>

        <el-col :span="12">
          <el-card
            shadow="hover"
            style="height: 100%; cursor: pointer; border: none"
            body-style="padding: 48px 24px"
            @click="navigateToTests"
          >
            <div style="text-align: center">
              <div
                style="
                  display: inline-block;
                  padding: 16px;
                  background: #67c23a;
                  border-radius: 16px;
                  margin-bottom: 24px;
                "
              >
                <el-icon size="48" color="white">
                  <Document />
                </el-icon>
              </div>
              <h3 style="font-size: 24px; font-weight: 600; color: #303133; margin-bottom: 12px">
                시험 관리
              </h3>
              <p style="color: #606266">
                시험 생성, 정답 관리, 성적 확인 및 피드백 제공까지 원스톱 서비스
              </p>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <!-- Features Overview -->
      <el-card
        shadow="never"
        style="
          max-width: 1000px;
          margin: 0 auto;
          background: linear-gradient(135deg, #f5f7fa, #ffffff);
          border: none;
        "
      >
        <div style="padding: 32px">
          <h4
            style="
              font-size: 24px;
              font-weight: 600;
              color: #303133;
              margin-bottom: 24px;
              text-align: center;
            "
          >
            주요 기능
          </h4>
          <el-row :gutter="24">
            <el-col :span="6">
              <div style="text-align: center">
                <div
                  style="
                    width: 48px;
                    height: 48px;
                    background: #409eff;
                    border-radius: 50%;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    margin: 0 auto 12px;
                  "
                >
                  <span style="color: white; font-weight: bold; font-size: 18px">1</span>
                </div>
                <h5 style="font-weight: 600; color: #303133; margin-bottom: 8px">학생 정보 관리</h5>
                <p style="font-size: 14px; color: #909399">체계적인 학생 데이터베이스</p>
              </div>
            </el-col>
            <el-col :span="6">
              <div style="text-align: center">
                <div
                  style="
                    width: 48px;
                    height: 48px;
                    background: #67c23a;
                    border-radius: 50%;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    margin: 0 auto 12px;
                  "
                >
                  <span style="color: white; font-weight: bold; font-size: 18px">2</span>
                </div>
                <h5 style="font-weight: 600; color: #303133; margin-bottom: 8px">시험 문제 생성</h5>
                <p style="font-size: 14px; color: #909399">간편한 시험 출제 도구</p>
              </div>
            </el-col>
            <el-col :span="6">
              <div style="text-align: center">
                <div
                  style="
                    width: 48px;
                    height: 48px;
                    background: #e6a23c;
                    border-radius: 50%;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    margin: 0 auto 12px;
                  "
                >
                  <span style="color: white; font-weight: bold; font-size: 18px">3</span>
                </div>
                <h5 style="font-weight: 600; color: #303133; margin-bottom: 8px">성적 자동 계산</h5>
                <p style="font-size: 14px; color: #909399">정확한 채점 및 분석</p>
              </div>
            </el-col>
            <el-col :span="6">
              <div style="text-align: center">
                <div
                  style="
                    width: 48px;
                    height: 48px;
                    background: #f56c6c;
                    border-radius: 50%;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    margin: 0 auto 12px;
                  "
                >
                  <span style="color: white; font-weight: bold; font-size: 18px">4</span>
                </div>
                <h5 style="font-weight: 600; color: #303133; margin-bottom: 8px">피드백 관리</h5>
                <p style="font-size: 14px; color: #909399">개인별 맞춤 지도</p>
              </div>
            </el-col>
          </el-row>
        </div>
      </el-card>
    </div>
  </div>
</template>
