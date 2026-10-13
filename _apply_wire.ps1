$ErrorActionPreference = 'Stop'
$root = 'd:/java_practice/FlashSaleSystem/'
$rel  = 'seckill-order/src/main/java/com/practice/flashsale/order/service/impl/OrderServiceImpl.java'

function TrimTrailingEmpty {
    param([string[]]$a)
    $n = $a.Count
    while ($n -gt 0 -and $a[$n - 1] -eq '') { $n-- }
    if ($n -lt 1) { return @() }
    return @($a[0..($n - 1)])
}

# 1. current worktree content (git reads it decrypted)
$before = & git --no-pager diff --no-index -- NUL $rel
$lines = @($before[6..($before.Count - 1)] | ForEach-Object { $_.Substring(1) })
Write-Output ("extracted lines = " + $lines.Count)
if ($lines.Count -ne 372) { throw "unexpected line count" }

# 2. new code blocks (utf-8)
$txt = [IO.File]::ReadAllText($root + '_newblocks.txt', [Text.Encoding]::UTF8)
$txt = $txt.Replace("`r`n", "`n")
$parts = $txt -split "`n@@@`n"
if ($parts.Count -ne 2) { throw ("unexpected block count: " + $parts.Count) }
$body = TrimTrailingEmpty ($parts[0] -split "`n")
$conv = TrimTrailingEmpty ($parts[1] -split "`n")
Write-Output ("body lines = " + $body.Count + ", conv lines = " + $conv.Count)
if ($body.Count -ne 46) { throw "body lines mismatch" }
if ($conv.Count -ne 22) { throw "conv lines mismatch" }

# 3. apply edits bottom-up
$L = New-Object System.Collections.ArrayList
foreach ($l in $lines) { [void]$L.Add($l) }

$convBlock = @($conv) + @('')
$L.InsertRange(201, [string[]]$convBlock)      # insert convertDeductResult before old line 202
$L.RemoveRange(137, 47)                        # old lines 138..184
$L.InsertRange(137, [string[]]$body)
$L.RemoveRange(52, 4)                          # old lines 53..56
foreach ($i in 15, 10, 9, 5) { $L.RemoveAt($i) }
$L.InsertRange(19, [string[]]@('import com.practice.flashsale.order.enums.SeckillStockDeductResultEnum;'))
$L.InsertRange(18, [string[]]@('import com.practice.flashsale.common.model.dto.SeckillActivityGoodsMetaDTO;', 'import com.practice.flashsale.common.utils.DateTimeUtils;', 'import com.practice.flashsale.common.utils.JsonUtils;'))

Write-Output ("final lines = " + $L.Count)
if ($L.Count -ne 390) { throw "final line count mismatch" }

# 4. write new content (LF) and stage it as a blob
$out = (($L | ForEach-Object { $_ }) -join "`n") + "`n"
[IO.File]::WriteAllText($root + '_new_OrderServiceImpl.txt', $out, (New-Object Text.UTF8Encoding($false)))

$sha = (& git hash-object -w ($root + '_new_OrderServiceImpl.txt')).Trim()
Write-Output ("blob = " + $sha)
& git update-index --add --cacheinfo "100644,$sha,$rel"
Write-Output '=== staged diff stat ==='
& git --no-pager diff --cached --stat -- $rel
