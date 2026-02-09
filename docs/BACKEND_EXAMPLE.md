# 简单的 Node.js 后端示例

这是一个简单的测试后端，用于接收语音转录文字。

## 快速启动

### 1. 安装依赖

```bash
npm install express body-parser cors
```

### 2. 创建 server.js

```javascript
const express = require('express');
const bodyParser = require('body-parser');
const cors = require('cors');

const app = express();
const PORT = 3000;

// 中间件
app.use(cors());
app.use(bodyParser.json());
app.use(bodyParser.urlencoded({ extended: true }));

// 日志中间件
app.use((req, res, next) => {
  console.log(`${new Date().toISOString()} - ${req.method} ${req.path}`);
  next();
});

// 接收转录文字的 API
app.post('/api/transcription', (req, res) => {
  const { text, timestamp } = req.body;
  
  console.log('收到转录文字:');
  console.log('  文字:', text);
  console.log('  时间戳:', new Date(timestamp).toLocaleString());
  
  // 这里可以添加你的业务逻辑
  // 例如：保存到数据库、调用 AI 接口等
  
  // 返回响应
  res.json({
    success: true,
    message: `已收到您的消息："${text}"`,
    data: {
      receivedAt: new Date().toISOString(),
      processedText: text.toUpperCase(), // 示例处理
    }
  });
});

// 健康检查
app.get('/health', (req, res) => {
  res.json({ status: 'ok', timestamp: new Date().toISOString() });
});

// 启动服务器
app.listen(PORT, '0.0.0.0', () => {
  console.log(`服务器运行在 http://0.0.0.0:${PORT}`);
  console.log('API 端点: POST /api/transcription');
});
```

### 3. 运行服务器

```bash
node server.js
```

### 4. 配置 Android 应用

如果在本地测试：

1. 获取电脑的局域网 IP（例如：192.168.1.100）
2. 在 `ApiService.kt` 中修改：
   ```kotlin
   private const val BASE_URL = "http://192.168.1.100:3000/"
   ```
3. 确保手机和电脑在同一局域网

### 5. 测试 API

```bash
# 使用 curl 测试
curl -X POST http://localhost:3000/api/transcription \
  -H "Content-Type: application/json" \
  -d '{"text":"你好世界","timestamp":1702290000000}'
```

## 使用云服务部署

### 部署到 Heroku

1. 创建 `Procfile`：
   ```
   web: node server.js
   ```

2. 部署：
   ```bash
   heroku create
   git push heroku main
   ```

### 部署到 Vercel

1. 创建 `vercel.json`：
   ```json
   {
     "version": 2,
     "builds": [
       {
         "src": "server.js",
         "use": "@vercel/node"
       }
     ],
     "routes": [
       {
         "src": "/(.*)",
         "dest": "/server.js"
       }
     ]
   }
   ```

2. 部署：
   ```bash
   vercel
   ```

## Python Flask 版本

```python
from flask import Flask, request, jsonify
from flask_cors import CORS
from datetime import datetime

app = Flask(__name__)
CORS(app)

@app.route('/api/transcription', methods=['POST'])
def transcription():
    data = request.json
    text = data.get('text', '')
    timestamp = data.get('timestamp', 0)
    
    print(f'收到转录文字: {text}')
    print(f'时间戳: {datetime.fromtimestamp(timestamp/1000)}')
    
    return jsonify({
        'success': True,
        'message': f'已收到您的消息："{text}"',
        'data': {
            'receivedAt': datetime.now().isoformat(),
            'processedText': text.upper()
        }
    })

@app.route('/health')
def health():
    return jsonify({
        'status': 'ok',
        'timestamp': datetime.now().isoformat()
    })

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=3000, debug=True)
```

运行：
```bash
pip install flask flask-cors
python server.py
```

## 集成 AI 服务示例

### 集成 OpenAI

```javascript
const OpenAI = require('openai');

const openai = new OpenAI({
  apiKey: process.env.OPENAI_API_KEY
});

app.post('/api/transcription', async (req, res) => {
  const { text } = req.body;
  
  try {
    // 调用 GPT
    const completion = await openai.chat.completions.create({
      model: "gpt-3.5-turbo",
      messages: [
        { role: "user", content: text }
      ],
    });
    
    const aiResponse = completion.choices[0].message.content;
    
    res.json({
      success: true,
      message: aiResponse,
      data: { originalText: text }
    });
  } catch (error) {
    console.error('AI 错误:', error);
    res.status(500).json({
      success: false,
      message: '处理失败',
    });
  }
});
```

## 注意事项

- 生产环境请添加身份验证
- 使用 HTTPS 保护数据传输
- 添加速率限制防止滥用
- 实现错误处理和日志记录
