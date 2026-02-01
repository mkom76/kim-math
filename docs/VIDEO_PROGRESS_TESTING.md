# Video Progress Tracking - Testing Guide

## Manual Testing Checklist

### Student View - Progress Tracking

#### Basic Functionality
- [ ] Play video and wait 30 seconds → Progress updates (check network tab)
- [ ] Watch video at 1x speed for 1 minute → Progress increases correctly
- [ ] Watch video at 2x speed for 1 minute → Progress increases correctly
- [ ] Refresh page → Progress persists
- [ ] Watch to 90% → Completion checkmark appears

#### Skip Detection
- [ ] Jump forward 5 minutes → Progress does NOT update (check console for "Skip detected")
- [ ] Watch normally after skip → Progress resumes updating from current position
- [ ] 2x speed playback for 30 seconds (60s of video) → Progress updates normally

#### Progress Rollback Prevention
- [ ] Watch to 50%, then seek back to 20% → Progress stays at 50%
- [ ] Watch to 100% (completed), then restart video → Stays marked as completed

#### UI Display
- [ ] Completed video shows green progress bar + checkmark + "시청 완료"
- [ ] In-progress video shows blue progress bar + "N% 시청중"
- [ ] Unwatched video shows gray bar + "미시청"
- [ ] Last watched time displays correctly ("5분 전", "2시간 전", etc.)

#### Edge Cases
- [ ] Close dialog mid-video → Progress saved up to last 30-second interval
- [ ] Network failure during update → Fails silently, no error toast
- [ ] Open multiple videos in different tabs → Each tracks independently

### Performance Testing

#### Load Test (if possible)
- [ ] Simulate 10+ students watching simultaneously
- [ ] Check server CPU/memory usage
- [ ] Verify database queries are efficient (check slow query log)

---

## Test Scenarios

### Scenario 1: Normal Viewing
1. Student opens video
2. Watches for 2 minutes at 1x speed
3. Expected: 4 progress updates (30s, 1min, 1min30s, 2min)
4. Progress bar shows ~33% (if 6-minute video)

### Scenario 2: Speed Watching
1. Student sets playback to 2x speed
2. Watches for 1 minute real time (= 2 minutes of video)
3. Expected: 2 progress updates (30s, 1min real time = 60s, 2min video time)
4. Progress updates correctly

### Scenario 3: Skip Attempt
1. Student watches to 1 minute
2. Skips to 5 minutes
3. Expected: No progress update, console shows "Skip detected, not updating progress"
4. Progress stays at 1 minute (or last saved position)

### Scenario 4: Completion
1. Student watches to 90% (e.g., 5:24 of 6:00 video)
2. Expected: Video marked as completed
3. Green progress bar + checkmark appears
4. Shows "시청 완료"

### Scenario 5: Resume Watching
1. Student watches to 50%
2. Closes browser
3. Returns next day
4. Expected: Progress bar shows 50%, "1일 전" timestamp

---

## Known Limitations

1. **Progress Update Interval**
   - Updates every 30 seconds
   - Up to 30 seconds of progress can be lost if browser crashes

2. **Skip Detection Threshold**
   - Allows up to 65 seconds of jump (to accommodate 2x speed)
   - 30s interval × 2.0 speed + 5s tolerance = 65s

3. **Multiple Devices**
   - Last updated device wins (potential race condition, but acceptable)
   - No conflict resolution between simultaneous views

4. **Browser Tab Inactive**
   - Background tab timers may be throttled by browser
   - Update interval might be irregular when tab is not focused

---

## Troubleshooting

### Progress not updating

1. **Check browser console**
   - Look for errors or "Failed to update progress" messages
   - Verify YouTube iframe API loaded (check for `window.YT`)

2. **Check network tab**
   - Look for `PUT /students/{id}/videos/{id}/progress` requests
   - Should see requests every 30 seconds during playback
   - Response should be 200 OK

3. **Verify login**
   - Student must be logged in
   - Check auth token is valid

### Skip detection too sensitive

1. **Verify constants**
   - `INTERVAL_SEC = 30`
   - `MAX_ALLOWED = 65`

2. **Check playback speed**
   - YouTube only supports up to 2x speed officially
   - Higher speeds may trigger skip detection

### Progress bar not showing

1. **Check Vue DevTools**
   - Inspect `progressMap` in StudentVideosView component
   - Verify data is populated

2. **Verify API response**
   - Check `GET /students/{id}/videos/progress` returns correct data
   - Ensure `videoId` matches video card's `video.id`

3. **Check console errors**
   - Look for failed API calls
   - Verify no TypeScript errors

---

## API Testing Examples

### Update Progress

```bash
curl -X PUT http://localhost:8080/api/students/1/videos/1/progress \
  -H "Content-Type: application/json" \
  -d '{
    "currentTime": 120,
    "duration": 600
  }'
```

**Expected Response:**
```json
{
  "videoId": 1,
  "currentTime": 120,
  "duration": 600,
  "progressPercent": 20,
  "completed": false,
  "lastWatchedAt": "2026-02-01T14:30:00"
}
```

### Get Student Progress

```bash
curl http://localhost:8080/api/students/1/videos/progress
```

**Expected Response:**
```json
[
  {
    "videoId": 1,
    "currentTime": 120,
    "duration": 600,
    "progressPercent": 20,
    "completed": false,
    "lastWatchedAt": "2026-02-01T14:30:00"
  },
  {
    "videoId": 2,
    "currentTime": 540,
    "duration": 600,
    "progressPercent": 90,
    "completed": true,
    "lastWatchedAt": "2026-02-01T13:15:00"
  }
]
```

### Test Cases

#### 1. Normal Progress Update
- Send `currentTime: 60, duration: 600`
- Verify `progressPercent` is 10
- Verify `completed` is false

#### 2. Completion at 90%
- Send `currentTime: 540, duration: 600`
- Verify `progressPercent` is 90
- Verify `completed` is true

#### 3. Rollback Prevention
- First: Send `currentTime: 180, duration: 600`
- Then: Send `currentTime: 120, duration: 600`
- Verify `currentTime` stays at 180 (not rolled back)

#### 4. Invalid Input
- Send empty body → Should return 400 Bad Request
- Send negative `currentTime` → Should handle gracefully
- Send `duration: 0` → Should handle division by zero

---

## Database Verification

### Check Progress Records

```sql
SELECT
  svp.id,
  s.name as student_name,
  lv.title as video_title,
  svp.current_time,
  svp.duration,
  ROUND((svp.current_time * 100.0) / svp.duration, 2) as progress_percent,
  svp.completed,
  svp.last_watched_at
FROM student_video_progress svp
JOIN students s ON svp.student_id = s.id
JOIN lesson_videos lv ON svp.lesson_video_id = lv.id
ORDER BY svp.last_watched_at DESC
LIMIT 10;
```

### Find Incomplete Videos

```sql
SELECT
  s.name as student_name,
  lv.title as video_title,
  ROUND((svp.current_time * 100.0) / svp.duration, 2) as progress_percent
FROM student_video_progress svp
JOIN students s ON svp.student_id = s.id
JOIN lesson_videos lv ON svp.lesson_video_id = lv.id
WHERE svp.completed = false
  AND svp.current_time > 0
ORDER BY svp.last_watched_at DESC;
```

### Check Completion Rate

```sql
SELECT
  COUNT(*) as total_videos_watched,
  SUM(CASE WHEN completed = true THEN 1 ELSE 0 END) as completed_videos,
  ROUND(
    SUM(CASE WHEN completed = true THEN 1 ELSE 0 END) * 100.0 / COUNT(*),
    2
  ) as completion_rate_percent
FROM student_video_progress
WHERE current_time > 0;
```

---

## Performance Benchmarks

### Expected Performance (50 concurrent students)

- **Request Rate**: 100 requests/min (50 students × 1 update per 30s)
- **Database Load**: Minimal (simple UPDATE queries with indexes)
- **Response Time**: < 100ms per update request
- **Memory Usage**: Negligible (stateless updates)

### Monitor These Metrics

1. **API Response Time**
   - 95th percentile should be < 200ms
   - 99th percentile should be < 500ms

2. **Database Query Time**
   - UPDATE query should be < 10ms
   - SELECT query should be < 5ms

3. **Error Rate**
   - Should be < 1% (network failures are acceptable)
   - 5xx errors should be 0%

4. **Database Connections**
   - Should not grow with number of students
   - Connection pool should be sufficient

---

## Acceptance Criteria

This feature is ready for production when:

- [ ] All manual test cases pass
- [ ] No console errors during normal playback
- [ ] Progress persists across page refreshes
- [ ] Skip detection prevents cheating
- [ ] Rollback prevention works correctly
- [ ] UI displays all three states (completed/in-progress/unwatched)
- [ ] API returns correct data structure
- [ ] Database queries use proper indexes
- [ ] 10+ concurrent students can watch without issues
- [ ] Documentation is accurate and complete
