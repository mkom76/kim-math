# YouTube Data API v3 Setup Guide

## 1. Create Google Cloud Project

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project or select an existing one
3. Note the project name

## 2. Enable YouTube Data API v3

1. Navigate to "APIs & Services" > "Library"
2. Search for "YouTube Data API v3"
3. Click "Enable"

## 3. Create API Key

1. Navigate to "APIs & Services" > "Credentials"
2. Click "Create Credentials" > "API key"
3. Copy the generated API key
4. (Optional) Restrict the key:
   - Click "Edit API key"
   - Under "API restrictions", select "Restrict key"
   - Choose "YouTube Data API v3"
   - Save

## 4. Configure Application

Add the API key to your environment:

**Development:**
```bash
export YOUTUBE_API_KEY="your-api-key-here"
```

**Production (docker-compose.prod.yml):**
```yaml
environment:
  - YOUTUBE_API_KEY=${YOUTUBE_API_KEY}
```

**Server .env file:**
```
YOUTUBE_API_KEY=your-api-key-here
```

## 5. Quota Management

- Default quota: 10,000 units per day
- Video metadata fetch: 1 unit per request
- Monitor usage in Google Cloud Console > "APIs & Services" > "Dashboard"

## 6. Testing

Test the API integration:

1. Add a YouTube video URL in lesson detail view
2. Check that title, thumbnail, and duration are automatically fetched
3. Verify video playback in student videos view
