/**
 * 图标生成脚本
 *
 * ============================================================
 * 用途
 * ============================================================
 *   生成扩展所需的三个图标文件：icon16.png、icon48.png、icon128.png。
 *   使用 Node.js 的 Buffer API 手动构建最小的 PNG 文件。
 *
 *   生成的图标是纯色方块（紫色 #6c5ce7），作为占位符使用。
 *   后续可以替换为正式设计的图标。
 *
 * 使用方式：
 *   node generate-icons.js
 *
 * 注意：生成的 PNG 文件结构：
 *   PNG 签名 (8 字节) + IHDR 块 + IDAT 块（压缩的像素数据）+ IEND 块
 *   我们使用 "none" 压缩方法（zlib method 0），这样不需要依赖任何压缩库。
 */

const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

// 图标尺寸列表
const sizes = [16, 48, 128];

// 紫色 #6c5ce7 → RGB (108, 92, 231)
const COLOR = { r: 108, g: 92, b: 231 };

// 确保 icons 目录存在
const iconsDir = path.join(__dirname, 'icons');
if (!fs.existsSync(iconsDir)) {
  fs.mkdirSync(iconsDir, { recursive: true });
}

for (const size of sizes) {
  const png = createPng(size, size, COLOR);
  const filePath = path.join(iconsDir, `icon${size}.png`);
  fs.writeFileSync(filePath, png);
  console.log(`Created: icon${size}.png (${png.length} bytes)`);
}

console.log('Done! Icons generated in extension/icons/');

// ============================================================
// PNG 生成工具函数
// ============================================================

/**
 * 创建一个指定尺寸的纯色 PNG 图片
 *
 * PNG 文件格式：
 *   1. PNG 签名：8 字节，标识这是一个 PNG 文件
 *   2. IHDR 块：图片头信息（宽度、高度、颜色类型等）
 *   3. IDAT 块：压缩的像素数据
 *   4. IEND 块：文件结束标记
 *
 * 每个块的结构：
 *   [长度(4字节)] [类型(4字节)] [数据] [CRC32(4字节)]
 *
 * @param {number} width - 图片宽度
 * @param {number} height - 图片高度
 * @param {{r: number, g: number, b: number}} color - RGB 颜色值
 * @returns {Buffer} PNG 文件的二进制数据
 */
function createPng(width, height, color) {
  // PNG 签名：固定值，标识文件类型
  const signature = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);

  // IHDR 数据：13 字节
  // 宽度(4) + 高度(4) + 位深度(1) + 颜色类型(1) + 压缩方法(1) + 过滤方法(1) + 隔行扫描(1)
  const ihdrData = Buffer.alloc(13);
  ihdrData.writeUInt32BE(width, 0);
  ihdrData.writeUInt32BE(height, 4);
  ihdrData.writeUInt8(8, 8);       // 位深度：8（每个通道 8 位 = 256 色）
  ihdrData.writeUInt8(2, 9);       // 颜色类型：2（RGB 真彩色，不含透明通道）
  ihdrData.writeUInt8(0, 10);      // 压缩方法：0（标准 deflate）
  ihdrData.writeUInt8(0, 11);      // 过滤方法：0（标准）
  ihdrData.writeUInt8(0, 12);      // 隔行扫描：0（不使用）

  const ihdr = createChunk('IHDR', ihdrData);

  // 构建原始像素数据
  // PNG 的每行像素前面需要一个过滤类型字节（0 = None，不做任何过滤）
  // 所以每行的字节数 = 1（过滤类型）+ width * 3（RGB 每像素 3 字节）
  const rowSize = 1 + width * 3;
  const rawData = Buffer.alloc(height * rowSize);

  for (let y = 0; y < height; y++) {
    const rowOffset = y * rowSize;
    rawData[rowOffset] = 0; // 过滤类型：None
    for (let x = 0; x < width; x++) {
      const pixelOffset = rowOffset + 1 + x * 3;
      rawData[pixelOffset] = color.r;
      rawData[pixelOffset + 1] = color.g;
      rawData[pixelOffset + 2] = color.b;
    }
  }

  // 使用 zlib 压缩像素数据
  const compressedData = zlib.deflateSync(rawData);
  const idat = createChunk('IDAT', compressedData);

  // IEND 块：空数据，标记 PNG 文件结束
  const iend = createChunk('IEND', Buffer.alloc(0));

  return Buffer.concat([signature, ihdr, idat, iend]);
}

/**
 * 创建一个 PNG 块
 *
 * PNG 块结构：[长度(4字节)] [类型(4字节)] [数据(N字节)] [CRC32(4字节)]
 * CRC32 校验覆盖"类型 + 数据"部分，确保数据完整性。
 *
 * @param {string} type - 块类型（4 个 ASCII 字符，如 'IHDR', 'IDAT', 'IEND'）
 * @param {Buffer} data - 块数据
 * @returns {Buffer} 完整的 PNG 块
 */
function createChunk(type, data) {
  const typeBuffer = Buffer.from(type, 'ascii');
  const length = Buffer.alloc(4);
  length.writeUInt32BE(data.length, 0);

  // CRC32 校验覆盖类型 + 数据
  const crcData = Buffer.concat([typeBuffer, data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(crcData), 0);

  return Buffer.concat([length, typeBuffer, data, crc]);
}

/**
 * CRC32 校验和计算
 *
 * CRC32（Cyclic Redundancy Check）是一种数据完整性校验算法。
 * PNG 规范要求每个块都附带 CRC32 校验值，用于检测数据传输错误。
 *
 * 这里使用查表法（预计算 256 个 CRC 值），比逐位计算快很多。
 *
 * @param {Buffer} buf - 要计算校验和的数据
 * @returns {number} CRC32 值（无符号 32 位整数）
 */
function crc32(buf) {
  // 预计算 CRC 表（多项式：0xEDB88320，这是 CRC32 的标准多项式）
  let table = crc32.table;
  if (!table) {
    table = new Uint32Array(256);
    for (let i = 0; i < 256; i++) {
      let c = i;
      for (let j = 0; j < 8; j++) {
        c = (c & 1) ? (0xEDB88320 ^ (c >>> 1)) : (c >>> 1);
      }
      table[i] = c;
    }
    crc32.table = table;
  }

  let crc = 0xFFFFFFFF;
  for (let i = 0; i < buf.length; i++) {
    crc = table[(crc ^ buf[i]) & 0xFF] ^ (crc >>> 8);
  }
  return (crc ^ 0xFFFFFFFF) >>> 0;
}
