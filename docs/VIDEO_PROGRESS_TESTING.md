# Video Progress Tracking - Testing Guide

This document provides comprehensive testing procedures for the video progress tracking feature implementation.

## Table of Contents
- [Manual Testing Checklist](#manual-testing-checklist)
- [Performance Testing](#performance-testing)
- [Test Scenarios](#test-scenarios)
- [Known Limitations](#known-limitations)
- [Troubleshooting](#troubleshooting)
- [API Testing Examples](#api-testing-examples)

---

## Manual Testing Checklist

### Student View Progress Tracking

#### Basic Functionality
- [ ] **Play video from start**
  - Open a video that has not been watched
  - Click play and watch for 30+ seconds
  - Verify progress updates to the server
  - Check that `currentTime` is recorded correctly

- [ ] **1x speed playback**
  - Watch video at normal speed for 1 minute
  - Verify `playbackSpeed` is recorded as 1.0
  - Check that progress updates every 30 seconds

- [ ] **2x speed playback**
  - Change playback speed to 2x
  - Watch for 1 minute
  - Verify `playbackSpeed` is recorded as 2.0
  - Confirm progress updates correctly

- [ ] **Page refresh persistence**
  - Watch video to 50% completion
  - Refresh the page
  - Verify video resumes from last saved position
  - Check that progress bar shows correct percentage

- [ ] **90% completion threshold**
  - Watch video to exactly 90% completion
  - Verify video is marked as "completed"
  - Check that completion timestamp is recorded
  - Confirm video appears in "completed" section

#### Skip Detection
- [ ] **Jump forward detection**
  - Watch 1 minute of video at 1x speed
  - Manually seek forward 5 minutes
  - Verify skip is detected (should not save progress)
  - Check that progress remains at pre-skip position

- [ ] **Resume after skip attempt**
  - After skip is detected, continue watching normally
  - Verify progress tracking resumes after 65 seconds
  - Check that subsequent progress updates are saved

- [ ] **2x speed skip detection**
  - Watch 30 seconds at 2x speed (60 seconds of content)
  - Manually seek forward 5 minutes
  - Verify skip is still detected
  - Confirm playback speed is considered in skip calculation

#### Progress Rollback Prevention
- [ ] **Seek backward**
  - Watch video to 5:00
  - Seek back to 3:00
  - Verify progress does NOT rollback to 3:00
  - Confirm maxWatchedTime remains at 5:00

- [ ] **Restart after completion**
  - Complete a video (watch to 90%+)
  - Restart video from beginning
  - Verify completion status is maintained
  - Check that video still shows as "completed"

#### UI Display
- [ ] **Completed state**
  - Complete a video
  - Verify green checkmark appears
  - Check completion date is displayed
  - Confirm progress bar shows 100%

- [ ] **In-progress state**
  - Watch video to 40%
  - Navigate away and return
  - Verify yellow progress indicator
  - Check "Continue Watching" option appears

- [ ] **Unwatched state**
  - View a brand new video
  - Verify no progress bar is shown
  - Check "Start Watching" or similar prompt

- [ ] **Last watched time display**
  - Watch multiple videos partially
  - Verify each shows correct last watched timestamp
  - Check timestamp format is user-friendly

#### Edge Cases
- [ ] **Close video dialog mid-playback**
  - Start watching a video
  - Close the dialog after 45 seconds
  - Reopen the video
  - Verify progress was saved before closing

- [ ] **Network failure during playback**
  - Disable network connection
  - Watch video for 1 minute
  - Re-enable network
  - Verify progress catches up when connection restored

- [ ] **Multiple tabs**
  - Open same video in two browser tabs
  - Play in both tabs with slight delay
  - Verify no race condition issues
  - Check that most recent progress wins

---

## Performance Testing

### Load Testing Guidelines

#### Concurrent User Test
1. **Setup**: Prepare 10+ student accounts
2. **Execution**: Have all students watch videos simultaneously
3. **Monitoring**: Observe server response times
4. **Success Criteria**:
   - All progress updates complete within 5 seconds
   - No failed requests (check server logs)
   - UI remains responsive

#### Progress Update Load
1. **Setup**: Create script to simulate 100+ concurrent progress updates
2. **Test**: Send batch updates at 30-second intervals
3. **Monitor**: Database connection pool usage
4. **Success Criteria**:
   - 95% of requests complete within 2 seconds
   - No database deadlocks
   - Connection pool does not exhaust

### Server Monitoring

#### CPU Usage
- Baseline: Record CPU usage with no video playback
- Load: Monitor CPU during 10+ concurrent video sessions
- Threshold: CPU should remain below 70% usage
- Tools: `top`, `htop`, or server monitoring dashboard

#### Memory Usage
- Baseline: Record memory usage at idle
- Load: Monitor memory during peak usage
- Check for: Memory leaks (gradually increasing usage)
- Threshold: Memory should remain stable, not exceed 80% of available RAM

#### Database Query Efficiency
- Use `EXPLAIN ANALYZE` on progress update queries
- Verify indexes are being used on:
  - `student_video_progress.student_id`
  - `student_video_progress.video_id`
  - `student_video_progress.clinic_id`
- Check query execution time: Should be < 100ms

---

## Test Scenarios

### Scenario 1: Normal Viewing
**Objective**: Verify basic progress tracking works correctly

**Steps**:
1. Login as a student
2. Open a 5-minute video
3. Watch for 2 minutes at 1x speed
4. Wait for at least one 30-second update interval
5. Refresh the page

**Expected Results**:
- Video resumes at approximately 2:00 mark
- Progress bar shows ~40% completion
- `currentTime` in database is around 120 seconds
- `playbackSpeed` is 1.0
- Video is marked as "in-progress"

### Scenario 2: Speed Watching
**Objective**: Verify 2x speed tracking works correctly

**Steps**:
1. Login as a student
2. Open a 5-minute video
3. Set playback speed to 2x
4. Watch for 1 minute of real time (2 minutes of video content)
5. Check progress

**Expected Results**:
- `currentTime` is approximately 120 seconds
- `playbackSpeed` is 2.0
- Progress bar shows ~40% completion
- No skip detection triggered

### Scenario 3: Skip Attempt
**Objective**: Verify skip detection prevents cheating

**Steps**:
1. Login as a student
2. Open a 10-minute video
3. Watch normally for 1 minute
4. Manually seek forward to 5:00 mark
5. Continue watching from 5:00
6. Check progress after 30 seconds

**Expected Results**:
- Skip is detected (4-minute jump > 65-second threshold)
- Progress remains at ~1:00, not 5:00
- Console may show skip detection message
- After watching 65+ seconds from 5:00, tracking resumes normally

### Scenario 4: Completion
**Objective**: Verify 90% threshold marks video as completed

**Steps**:
1. Login as a student
2. Open a 10-minute video
3. Seek to 8:00 (80%)
4. Watch until 9:00 (90%)
5. Wait for progress update

**Expected Results**:
- Video is marked as "completed"
- `completedAt` timestamp is recorded
- Green checkmark appears in UI
- Progress bar shows 100% (or 90%+)
- Video moves to "Completed" section

### Scenario 5: Resume Watching
**Objective**: Verify students can resume from where they left off

**Steps**:
1. Login as a student
2. Open a 10-minute video
3. Watch to 4:00 (40%)
4. Close the video dialog
5. Navigate to another page
6. Return and reopen the same video

**Expected Results**:
- Video automatically seeks to 4:00
- Progress bar shows ~40% completion
- "Continue Watching" option is available
- Last watched timestamp is displayed

---

## Known Limitations

### 30-Second Update Interval
**Issue**: Progress is only saved every 30 seconds

**Impact**:
- If a student closes the browser before the 30-second mark, progress may not be saved
- Up to 30 seconds of progress can be lost in unexpected disconnections

**Mitigation**:
- Updates are also sent when video is paused or dialog is closed
- Most modern browsers keep connections alive during page transitions

### 65-Second Skip Threshold
**Issue**: Skip detection threshold is fixed at 65 seconds

**Impact**:
- Students can skip up to 65 seconds without detection
- Very short videos (< 2 minutes) may not have effective skip protection
- At 2x speed, students can skip up to 32.5 seconds of real content

**Considerations**:
- Threshold balances legitimate seeking vs. cheating
- Can be adjusted in `VideoPlayer.tsx` if needed

### Multi-Device Race Conditions
**Issue**: Watching the same video on multiple devices simultaneously

**Impact**:
- Progress updates may overwrite each other
- The last update wins (last-write-wins strategy)
- May result in slightly inaccurate progress tracking

**Mitigation**:
- Use timestamp-based conflict resolution
- Most students use single device per session
- Edge case unlikely in normal usage

### Browser Tab Inactive State
**Issue**: Some browsers throttle JavaScript when tab is inactive

**Impact**:
- Progress updates may be delayed if tab is in background
- Video may pause automatically in some browsers

**Behavior**:
- This is browser-dependent (Chrome, Firefox, Safari differ)
- Progress will catch up when tab becomes active again

---

## Troubleshooting

### Progress Not Updating

**Symptoms**:
- Video plays but progress is not saved
- Refreshing returns to video start
- No progress bar shown

**Debugging Steps**:
1. Open browser developer console (F12)
2. Check for JavaScript errors
3. Look for failed API requests in Network tab
4. Verify `updateVideoProgress` function is being called
5. Check server logs for error messages

**Common Causes**:
- Network connectivity issues
- Authentication token expired
- Server-side error in update endpoint
- Database connection issues

**Solutions**:
- Check network connection
- Re-login to refresh authentication
- Check server logs for specific errors
- Verify database is running and accessible

### Skip Detection Issues

**Symptoms**:
- Skips are not detected when seeking forward
- Progress updates even after large jumps
- Skip detection triggers on legitimate playback

**Debugging Steps**:
1. Check console for skip detection logs
2. Verify `lastUpdateTime` is being tracked correctly
3. Check playback speed calculation
4. Review skip threshold logic in code

**Common Causes**:
- Threshold set too high
- Playback speed not considered
- Race condition in update timing
- Video metadata loading delays

**Solutions**:
- Adjust `SKIP_THRESHOLD_SECONDS` if needed
- Ensure playback speed is included in calculations
- Add additional logging to track skip detection
- Test with videos of various lengths

### Progress Bar Not Showing

**Symptoms**:
- Video plays but no progress bar visible
- Progress percentage is incorrect
- Completed videos not showing checkmark

**Debugging Steps**:
1. Check if progress data exists in database
2. Verify API response includes progress data
3. Inspect component state in React DevTools
4. Check CSS styling for visibility issues

**Common Causes**:
- Progress data not loaded from API
- Component not re-rendering on data update
- CSS display property set to none
- Progress calculation error

**Solutions**:
- Verify API returns correct progress data
- Force component re-render or check dependencies
- Inspect element styles in browser DevTools
- Check progress calculation logic

### Video Not Resuming from Last Position

**Symptoms**:
- Video always starts from beginning
- Progress exists but seek not happening
- Intermittent resume behavior

**Debugging Steps**:
1. Verify progress data is loaded before video initializes
2. Check if seek operation is being called
3. Look for video metadata not loaded errors
4. Test with different video formats

**Common Causes**:
- Progress loaded after video player initialized
- Video metadata not ready for seek operation
- Async timing issues
- Video format doesn't support seeking

**Solutions**:
- Ensure progress data loads before mounting player
- Wait for `loadedmetadata` event before seeking
- Add retry logic for seek operations
- Test with known-good video formats

---

## API Testing Examples

### Update Video Progress

**cURL Command**:
```bash
curl -X POST https://your-domain.com/api/students/video-progress \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_AUTH_TOKEN" \
  -d '{
    "videoId": 123,
    "clinicId": 456,
    "currentTime": 180,
    "playbackSpeed": 1.0,
    "completed": false
  }'
```

**Expected Response** (200 OK):
```json
{
  "success": true,
  "data": {
    "id": 789,
    "studentId": 1,
    "videoId": 123,
    "clinicId": 456,
    "currentTime": 180,
    "maxWatchedTime": 180,
    "playbackSpeed": 1.0,
    "completed": false,
    "completedAt": null,
    "lastWatchedAt": "2026-02-01T10:30:00.000Z"
  }
}
```

**Test Cases**:
1. **First progress update**: Send with `currentTime: 60`
2. **Update existing progress**: Send with higher `currentTime: 120`
3. **Rollback attempt**: Send with lower `currentTime: 30` (should not decrease `maxWatchedTime`)
4. **Completion**: Send with `completed: true` (should set `completedAt`)
5. **Invalid data**: Send with negative `currentTime` (should return 400)

### Get Student Progress for a Video

**cURL Command**:
```bash
curl -X GET "https://your-domain.com/api/students/video-progress?videoId=123&clinicId=456" \
  -H "Authorization: Bearer YOUR_AUTH_TOKEN"
```

**Expected Response** (200 OK):
```json
{
  "success": true,
  "data": {
    "id": 789,
    "studentId": 1,
    "videoId": 123,
    "clinicId": 456,
    "currentTime": 180,
    "maxWatchedTime": 180,
    "playbackSpeed": 1.0,
    "completed": false,
    "completedAt": null,
    "lastWatchedAt": "2026-02-01T10:30:00.000Z"
  }
}
```

**Expected Response - No Progress** (200 OK):
```json
{
  "success": true,
  "data": null
}
```

**Test Cases**:
1. **Existing progress**: Query video with saved progress
2. **No progress**: Query unwatched video (should return null)
3. **Completed video**: Verify `completed: true` and `completedAt` timestamp
4. **Invalid videoId**: Should return 400 or appropriate error
5. **Invalid clinicId**: Should return 400 or appropriate error

### Get All Progress for a Clinic

**cURL Command**:
```bash
curl -X GET "https://your-domain.com/api/students/video-progress/clinic/456" \
  -H "Authorization: Bearer YOUR_AUTH_TOKEN"
```

**Expected Response** (200 OK):
```json
{
  "success": true,
  "data": [
    {
      "id": 789,
      "studentId": 1,
      "videoId": 123,
      "clinicId": 456,
      "currentTime": 180,
      "maxWatchedTime": 180,
      "playbackSpeed": 1.0,
      "completed": false,
      "completedAt": null,
      "lastWatchedAt": "2026-02-01T10:30:00.000Z"
    },
    {
      "id": 790,
      "studentId": 1,
      "videoId": 124,
      "clinicId": 456,
      "currentTime": 300,
      "maxWatchedTime": 300,
      "playbackSpeed": 2.0,
      "completed": true,
      "completedAt": "2026-02-01T09:45:00.000Z",
      "lastWatchedAt": "2026-02-01T09:45:00.000Z"
    }
  ]
}
```

**Test Cases**:
1. **Multiple progress records**: Student with several videos watched
2. **No progress**: New student with no videos watched (should return empty array)
3. **Mixed completion states**: Some completed, some in-progress
4. **Invalid clinicId**: Should return 400 or appropriate error

---

## Testing Checklist Summary

Before considering the video progress tracking feature complete, ensure:

- [ ] All manual testing checklist items pass
- [ ] Performance testing shows acceptable load handling
- [ ] All test scenarios produce expected results
- [ ] Known limitations are documented and understood
- [ ] Troubleshooting steps resolve common issues
- [ ] API endpoints work correctly with test data
- [ ] Database indexes are in place and effective
- [ ] Error handling covers edge cases
- [ ] UI displays all progress states correctly
- [ ] Multi-device scenarios are handled gracefully

---

## Additional Resources

- **Implementation Plan**: See project documentation for technical details
- **Database Schema**: `student_video_progress` table structure
- **API Documentation**: Backend endpoint specifications
- **Frontend Components**: `VideoPlayer.tsx`, `VideoList.tsx`

---

**Last Updated**: 2026-02-01
**Version**: 1.0
