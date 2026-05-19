# Utils-JVM Module

Pure JVM/Kotlin utility classes with no Android dependencies.

## Module Structure

```
utils-jvm/src/main/kotlin/com/taisau/android/common/utils/jvm/
├── StringUtils.kt      # String ops: capitalize, camelCase/snakeCase, Levenshtein, hex
├── DateUtils.kt        # Date/time: formatting, parsing, duration, age, quarter
├── RegexUtils.kt       # Pattern matching: email, phone, ID card, URL, IP, hex color
├── EncryptUtils.kt     # Crypto: MD5, SHA-1/256/512, AES-CBC, HMAC-SHA256, Base64
├── NumberUtils.kt      # Number ops: parse, clamp, range, format bytes, gcd/lcm, fib
├── RandomUtils.kt      # Random: int/long/float, strings, UUID, sampling, color
├── FileUtilsJvm.kt     # File I/O: read/write, copy/move, channel copy, find, tail
└── EncodeUtils.kt      # Encode: URL, Base64, GZIP, HTML/XML escape, Unicode, hex
```

## Usage

### StringUtils

```kotlin
StringUtils.isBlank("  ")                          // true
StringUtils.toCamelCase("user_name")                // userName
StringUtils.toSnakeCase("getUserList")              // get_user_list
StringUtils.truncate("hello world", 8)              // "hello..."
StringUtils.levenshteinDistance("kitten", "sitting")// 3
"48656c6c6f".hexToBytes()                          // ByteArray
```

### DateUtils

```kotlin
DateUtils.format(LocalDateTime.now())               // "2026-05-15 10:30:00"
DateUtils.parseDate("2026-05-15")                   // LocalDate
DateUtils.daysBetween(LocalDate.now(), LocalDate.of(2027, 1, 1))
DateUtils.getAge(LocalDate.of(1990, 1, 1))          // 36
DateUtils.isLeapYear(2024)                          // true
```

### RegexUtils

```kotlin
RegexUtils.isEmail("user@example.com")              // true
RegexUtils.isPhone("13800138000")                   // true
RegexUtils.isIdCard("110101199001011234")           // true (checksum verified)
RegexUtils.isUrl("https://jitpack.io")              // true
RegexUtils.isHexColor("#FF8800")                    // true
```

### EncryptUtils

```kotlin
EncryptUtils.md5("hello")                           // "5d41402abc4b2a76b9719d911017c592"
EncryptUtils.sha256("hello")                        // hex string
EncryptUtils.base64Encode("hello")                  // "aGVsbG8="
EncryptUtils.base64DecodeToString("aGVsbG8=")       // "hello"

val key = "0123456789abcdef".toByteArray()          // 16 bytes for AES-128
val iv = "abcdef0123456789".toByteArray()
val encrypted = EncryptUtils.aesEncryptBase64("secret data", key, iv)
val decrypted = EncryptUtils.aesDecryptBase64(encrypted, key, iv)

EncryptUtils.md5File(File("data.bin"))              // file MD5 checksum
```

### NumberUtils

```kotlin
NumberUtils.parseInt("42")                          // 42
NumberUtils.parseInt("abc", -1)                     // -1
NumberUtils.clamp(150, 0, 100)                      // 100
NumberUtils.formatBytes(1_073_741_824)              // "1.0 GB"
NumberUtils.gcd(12, 18)                             // 6
NumberUtils.lcm(12, 18)                             // 36
```

### RandomUtils

```kotlin
RandomUtils.randomInt(1, 100)                       // random int 1-100
RandomUtils.randomNumeric(6)                        // "482910"
RandomUtils.randomAlphaNumeric(16)                  // "aB3kL9xR7mQ2pW1n"
RandomUtils.uuid()                                  // "550e8400-e29b-..."
RandomUtils.randomElement(listOf("a", "b", "c"))    // random element
RandomUtils.sample(1..100, 5)                       // 5 random numbers
```

### FileUtilsJvm

```kotlin
FileUtilsJvm.readText(File("data.txt"))
FileUtilsJvm.writeText(File("out.txt"), "content")
FileUtilsJvm.copy(src, dest, overwrite = true)
FileUtilsJvm.delete(dir)
FileUtilsJvm.getSize(File("data.bin"))              // bytes
FileUtilsJvm.findFiles(File("src"), "kt")           // all .kt files
FileUtilsJvm.tail(File("log.txt"), 10)              // last 10 lines
FileUtilsJvm.copyFileWithChannel(src, dest)          // NIO channel copy
```

### EncodeUtils

```kotlin
EncodeUtils.urlEncode("a b")                        // "a+b"
EncodeUtils.urlDecode("a+b")                        // "a b"
EncodeUtils.htmlEscape("<tag>")                     // "&lt;tag&gt;"
EncodeUtils.gzipCompress(data)                      // compressed ByteArray
EncodeUtils.unicodeEncode("中")                     // "\\u4e2d"
"4e2d".hexToBytes()                                 // ByteArray
```
