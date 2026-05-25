# === 鏈湴涓€閿増鏈嚜澧炲苟鎺ㄩ€佽嚦浜戠鎵撳寘鑴氭湰 (Local Bump & Auto-Deployer) ===
# 鑷姩鎻愬崌 versionCode 涓?versionName 灏忕増鏈紝蹇€熸彁浜や唬鐮佸苟鎺ㄩ€侊紝瑙﹀彂 GitHub 鑷姩鏋勫缓鍙婃帹閫佹洿鏂?
param (
    [string]$Notes = "" # 鍏佽浠庡閮ㄧ洿鎺ヤ紶鍏ユ洿鏂版棩蹇楋紝浠ラ€傞厤 AI 鍏ㄨ嚜鍔ㄦ棤浜哄€煎畧宸ヤ綔娴?)

$ErrorActionPreference = "Stop"

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "  Android Boilerplate 鑷姩鐗堟湰閫掑涓庝竴閿彂甯冨伐鍏? -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan

# 1. 瀹夊叏妫€鏌ワ細妫€娴嬪綋鍓嶇洰褰曟槸鍚﹀凡缁忓垵濮嬪寲浜?Git
if (-not (Test-Path ".git")) {
    Write-Host "[ERROR] 褰撳墠鐩綍灏氭湭鍒濆鍖?Git 浠撳簱锛屾棤娉曚娇鐢ㄨ嚜鍔ㄦ帹閫佸姛鑳斤紒" -ForegroundColor Red
    Write-Host "[INFO] 鎻愮ず: 璇峰厛鍦ㄩ」鐩牴鐩綍杩愯 'git init' 骞朵笌 GitHub 杩滅浠撳簱鍏宠仈銆? -ForegroundColor Yellow
    if ([string]::IsNullOrEmpty($Notes)) { Read-Host "鎸夊洖杞﹂敭閫€鍑?.." }
    Exit
}

$ConfigFile = "config.gradle"
if (-not (Test-Path $ConfigFile)) {
    Write-Host "[ERROR] 鎵句笉鍒板叏灞€閰嶇疆鏂囦欢 $ConfigFile锛? -ForegroundColor Red
    Exit
}

# 2. 璇诲彇骞舵彁鍙栧綋鍓嶇増鏈俊鎭?$ConfigContent = Get-Content -Path $ConfigFile -Raw -Encoding UTF8

$VersionCodeMatch = [regex]::Match($ConfigContent, 'versionCode\s*=\s*(\d+)')
$VersionNameMatch = [regex]::Match($ConfigContent, 'versionName\s*=\s*"([^"]+)"')

if (-not $VersionCodeMatch.Success -or -not $VersionNameMatch.Success) {
    Write-Host "[ERROR] 鏃犳硶浠?$ConfigFile 涓В鏋愬嚭鏈夋晥鐨勭増鏈彿閰嶇疆锛? -ForegroundColor Red
    Exit
}

[int]$OldCode = $VersionCodeMatch.Groups[1].Value
$OldName = $VersionNameMatch.Groups[1].Value

# 3. 鑷姩璁＄畻鏂扮殑鐗堟湰鍙?# versionCode 鐩存帴 +1
$NewCode = $OldCode + 1

# versionName 閫掑鏈€鍚庝竴浣嶅皬鐗堟湰鍙?(濡?1.0.4 -> 1.0.5)
$NameParts = $OldName.Split('.')
if ($NameParts.Length -ge 3) {
    [int]$Patch = $NameParts[2]
    $NewPatch = $Patch + 1
    $NewName = "$($NameParts[0]).$($NameParts[1]).$NewPatch"
} else {
    $NewName = "$OldName.1"
}

Write-Host "=== 鏈湴鐗堟湰妫€娴?===" -ForegroundColor Gray
Write-Host "   褰撳墠鐗堟湰: v$OldName (Code: $OldCode)" -ForegroundColor Gray
Write-Host "   鎷熷崌绾ц嚦: v$NewName (Code: $NewCode)" -ForegroundColor Yellow

# 4. 寮曞杈撳叆鏈鏇存柊鐨勫彂甯冩棩蹇?$ReleaseNotes = ""
if (-not [string]::IsNullOrEmpty($Notes)) {
    $ReleaseNotes = $Notes
    Write-Host "鑷姩閲囩敤浼犲叆鐨勬洿鏂版棩蹇? $ReleaseNotes" -ForegroundColor Yellow
} else {
    Write-Host ""
    $ReleaseNotes = Read-Host "璇疯緭鍏ユ湰娆℃洿鏂扮殑鏃ュ織璇存槑 (鍙€夛紝鍙洿鎺ュ洖杞?"
    if ([string]::IsNullOrWhiteSpace($ReleaseNotes)) {
        $ReleaseNotes = "甯歌鍔熻兘浼樺寲涓庢€ц兘鎻愬崌銆?
    }
}

# 5. 鏇存柊骞跺啓鍏?config.gradle
Write-Host "姝ｅ湪鏇存柊 $ConfigFile..." -ForegroundColor Gray
$NewConfigContent = $ConfigContent -replace 'versionCode\s*=\s*\d+', "versionCode = $NewCode"
$NewConfigContent = $NewConfigContent -replace 'versionName\s*=\s*"[^"]+"', "versionName = `"$NewName`""
Set-Content -Path $ConfigFile -Value $NewConfigContent -Encoding UTF8

try {
    # 6. 鎵ц Git 涓€閿彁浜ゆ祦绋?    Write-Host "姝ｅ湪鏆傚瓨鏈湴淇敼骞舵彁浜や簨鍔?.." -ForegroundColor Gray
    git add .
    
    $CommitMsg = "chore(release): bump version to v$NewName [code $NewCode]"
    git commit -m $CommitMsg -m $ReleaseNotes
    
    # 7. 鎵撲笂甯︽棩蹇楄鏄庣殑 Git Tags
    Write-Host "姝ｅ湪鏈湴鏍囪鐗堟湰鏍囩 v$NewName..." -ForegroundColor Gray
    git tag -a "v$NewName" -m "Version v$NewName" -m $ReleaseNotes

    # 8. 寮哄姏鎺ㄩ€佽嚦 GitHub
    Write-Host "姝ｅ湪鍚?GitHub 涓诲垎鏀帹閫佷唬鐮佸拰 Tags..." -ForegroundColor Yellow
    Write-Host "[INFO] 鎻愮ず: 杩欏皢鑷姩瑙﹀彂 GitHub Actions 鏋勫缓绛惧悕 Release APK锛?鍒嗛挓鍚庢墜鏈轰笂灏变細妫€娴嬪埌鏇存柊锛? -ForegroundColor Yellow
    
    git push origin main
    git push origin --tags

    Write-Host "=========================================" -ForegroundColor Green
    Write-Host "  涓€閿彂甯冩祦绋嬪渾婊℃垚鍔燂紒" -ForegroundColor Green
    Write-Host "  浜戠鏋勫缓娴佹按绾垮凡鎴愬姛婵€娲伙紝璇疯€愬績绛夊緟 2 鍒嗛挓宸﹀彸銆? -ForegroundColor Green
    Write-Host "  2鍒嗛挓鍚庣洿鎺ュ湪鎮ㄦ墜鏈轰笂鎵撳紑 App锛屽嵆鍙偣鍑绘洿鏂颁韩鐢ㄦ柊鐗堬紒" -ForegroundColor Green
    Write-Host "=========================================" -ForegroundColor Green

} catch {
    Write-Host "[ERROR] 杩愯 Git 鍛戒护鏃堕亣鍒颁弗閲嶉敊璇紝鎿嶄綔涓柇锛? -ForegroundColor Red
    Write-Host "[INFO] 璇风‘淇濇偍宸查厤缃湰鍦?Git 鐢ㄦ埛鍚嶄笌閭锛屼笖杩滅瀛樺湪瀵瑰簲鐨?'main' 鍒嗘敮銆? -ForegroundColor Yellow
    
    # 鍙戠敓寮傚父锛屽洖婊氭湰鍦?config.gradle锛岄槻姝㈢増鏈彿涓嶄竴鑷?    Set-Content -Path $ConfigFile -Value $ConfigContent -Encoding UTF8
    Write-Host "鍏ㄥ眬閰嶇疆鏂囦欢 $ConfigFile 宸茶嚜鍔ㄥ洖婊氬鍘熴€? -ForegroundColor Gray
}

# 濡傛灉娌℃湁浼犲叆 Notes 鍙傛暟锛堣鏄庢槸浜哄伐浜や簰妯″紡锛夛紝绛夊緟鍥炶溅鍚庡叧闂獥鍙ｏ紱鍚﹀垯鐩存帴閫€鍑猴紝涓嶉樆濉?if ([string]::IsNullOrEmpty($Notes)) {
    Read-Host "鎸夊洖杞﹂敭閫€鍑?.."
}
