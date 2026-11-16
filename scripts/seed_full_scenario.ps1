# 在云镜推演里创建 26 节点全量测试工程
$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8080'

function Post($url, $obj) {
    $json = $obj | ConvertTo-Json -Depth 10 -Compress
    $bytes = [Text.Encoding]::UTF8.GetBytes($json)
    $r = Invoke-WebRequest -Uri "$base$url" -Method Post -ContentType 'application/json; charset=utf-8' -Body $bytes -UseBasicParsing
    $text = [Text.Encoding]::UTF8.GetString($r.RawContentStream.ToArray())
    return $text | ConvertFrom-Json
}

# 1) 工程
$proj = Post '/api/projects' @{ name = '测试-电商全量26节点'; description = '26 节点 / 47 连线 / 2 冗余组，用于回归与演示' }
$projectId = $proj.data.id
Write-Host "工程 id=$projectId"

$nodeIds = @{}
$portIds = @{}

function AddNode($key, $name, $kind, $x, $y, $spec, $ports) {
    $body = @{ name = $name; nodeKind = $kind; posX = $x; posY = $y }
    foreach ($k in $spec.Keys) { $body[$k] = $spec[$k] }
    if ($ports -and $ports.Count -gt 0) { $body['ports'] = $ports }
    $res = Post "/api/projects/$projectId/nodes" $body
    $nodeIds[$key] = $res.data.id
    foreach ($p in $res.data.ports) { $portIds["$key`:$($p.port)"] = $p.id }
    Write-Host ("  节点 {0,-4} {1}" -f $key, $name)
}

# 2) 资源池
AddNode 'H1' '物理机-应用区' 'host' 80 60 @{ cpuCores = 16; memTotalMb = 32768; diskGb = 500 } @()
AddNode 'H2' '物理机-数据区' 'host' 420 60 @{ cpuCores = 16; memTotalMb = 32768; diskGb = 1000 } @()
AddNode 'H3' '虚拟机-边缘区' 'vm' 760 60 @{ cpuCores = 8; memTotalMb = 16384; diskGb = 200 } @()
AddNode 'K1' 'K8s-生产集群' 'k8s_cluster' 80 220 @{ cpuCores = 16; memTotalMb = 32768; diskGb = 500 } @()

# 3) 入口层
AddNode 'G1' '网关-公网' 'gateway' 360 220 @{ cpuRequest = 1; memRequestMb = 1024 } @(
    @{ port = 443; protocol = 'tcp'; scope = 'public'; hostPort = 443; purpose = 'HTTPS 入口' },
    @{ port = 80; protocol = 'tcp'; scope = 'public'; hostPort = 80; purpose = 'HTTP 跳转' },
    @{ port = 8443; protocol = 'tcp'; scope = 'public'; hostPort = 8443; purpose = '映射到 Ingress' }
)
AddNode 'G2' '网关-内网A' 'gateway' 560 220 @{ cpuRequest = 0.5; memRequestMb = 512 } @(
    @{ port = 8080; protocol = 'tcp'; scope = 'internal'; purpose = '内网入口' }
)
AddNode 'G3' '网关-内网B' 'gateway' 720 220 @{ cpuRequest = 0.5; memRequestMb = 512 } @(
    @{ port = 8080; protocol = 'tcp'; scope = 'internal'; purpose = '内网入口' }
)
AddNode 'I1' 'Ingress-集群' 'ingress' 460 340 @{ cpuRequest = 0.5; memRequestMb = 512 } @(
    @{ port = 8080; protocol = 'tcp'; scope = 'internal'; purpose = '集群入口' }
)

# 4) 应用服务
AddNode 'S1' '订单服务' 'service' 60 420 @{ cpuRequest = 2; memRequestMb = 4096 } @(
    @{ port = 8081; protocol = 'tcp'; scope = 'internal' },
    @{ port = 9091; protocol = 'tcp'; scope = 'internal'; purpose = '健康检查' }
)
AddNode 'S2' '用户服务' 'service' 240 420 @{ cpuRequest = 1; memRequestMb = 2048 } @(@{ port = 8082; protocol = 'tcp'; scope = 'internal' })
AddNode 'S3' '商品服务' 'service' 420 420 @{ cpuRequest = 1; memRequestMb = 2048 } @(@{ port = 8083; protocol = 'tcp'; scope = 'internal' })
AddNode 'S4' '支付服务' 'service' 600 420 @{ cpuRequest = 1; memRequestMb = 2048 } @(@{ port = 8084; protocol = 'tcp'; scope = 'internal' })
AddNode 'S5' '库存服务' 'service' 780 420 @{ cpuRequest = 1; memRequestMb = 2048 } @(@{ port = 8085; protocol = 'tcp'; scope = 'internal' })
AddNode 'S6' '通知服务' 'service' 960 420 @{ cpuRequest = 0.5; memRequestMb = 1024 } @(@{ port = 8086; protocol = 'tcp'; scope = 'internal' })

# 5) 中间件
AddNode 'M1' 'MySQL-主' 'middleware' 60 620 @{ cpuRequest = 4; memRequestMb = 8192; diskGb = 200 } @(@{ port = 3306; protocol = 'tcp'; scope = 'internal' })
AddNode 'M2' 'MySQL-备' 'middleware' 240 620 @{ cpuRequest = 4; memRequestMb = 8192; diskGb = 200 } @(@{ port = 3306; protocol = 'tcp'; scope = 'internal' })
AddNode 'M3' 'Redis-主' 'middleware' 420 620 @{ cpuRequest = 2; memRequestMb = 4096 } @(@{ port = 6379; protocol = 'tcp'; scope = 'internal' })
AddNode 'M4' 'Redis-备' 'middleware' 600 620 @{ cpuRequest = 2; memRequestMb = 4096 } @(@{ port = 6379; protocol = 'tcp'; scope = 'internal' })
AddNode 'M5' 'Kafka' 'middleware' 780 620 @{ cpuRequest = 2; memRequestMb = 4096; diskGb = 200 } @(@{ port = 9092; protocol = 'tcp'; scope = 'internal' })

# 6) 存储与外部
AddNode 'O1' '对象存储-商品图' 'object_storage' 960 620 @{} @()
AddNode 'E1' '支付宝' 'external' 1140 620 @{} @()
AddNode 'E2' '短信平台' 'external' 1320 620 @{} @()

# 7) 工作负载
AddNode 'C1' '容器-订单' 'container' 60 800 @{ cpuRequest = 1; memRequestMb = 2048 } @(@{ port = 8081; protocol = 'tcp'; scope = 'internal' })
AddNode 'C2' '容器-用户' 'container' 240 800 @{ cpuRequest = 0.5; memRequestMb = 1024 } @(@{ port = 8082; protocol = 'tcp'; scope = 'internal' })
AddNode 'C3' '容器-商品' 'container' 420 800 @{ cpuRequest = 0.5; memRequestMb = 1024 } @(@{ port = 8083; protocol = 'tcp'; scope = 'internal' })
AddNode 'P1' 'Pod-支付' 'pod' 600 800 @{ cpuRequest = 1; memRequestMb = 2048 } @(@{ port = 8084; protocol = 'tcp'; scope = 'internal' })

function AddEdge($kind, $from, $to, $fromPort, $toPort) {
    $body = @{
        edgeKind = $kind
        sourceNodeId = $nodeIds[$from]
        targetNodeId = $nodeIds[$to]
    }
    if ($fromPort) { $body['sourcePortId'] = $portIds["$from`:$fromPort"] }
    if ($toPort) { $body['targetPortId'] = $portIds["$to`:$toPort"] }
    Post "/api/projects/$projectId/edges" $body | Out-Null
    Write-Host "  连线 $kind $from -> $to"
}

# 8) 部署
foreach ($pair in @(@('G1','H3'), @('G2','H3'), @('G3','H3'), @('I1','H1'), @('K1','H1'),
                    @('S1','H1'), @('S2','H1'), @('S3','H1'), @('S4','H1'), @('S5','H1'), @('S6','H1'),
                    @('C1','K1'), @('C2','K1'), @('C3','K1'), @('P1','K1'),
                    @('M1','H2'), @('M2','H2'), @('M3','H2'), @('M4','H2'), @('M5','H2'))) {
    AddEdge 'deploy_on' $pair[0] $pair[1] $null $null
}

# 9) 网络可达
AddEdge 'network_link' 'H1' 'H2' $null $null
AddEdge 'network_link' 'H1' 'H3' $null $null
AddEdge 'network_link' 'H2' 'H3' $null $null

# 10) 端口映射 / 主备复制
AddEdge 'replicate' 'M1' 'M2' 3306 3306
AddEdge 'replicate' 'M3' 'M4' 6379 6379

# 11) 调用依赖（22 条）
$deps = @(
    @('G1','G2',443,8080), @('G1','G3',443,8080), @('G2','I1',8080,8080), @('G3','I1',8080,8080),
    @('I1','S1',8080,8081), @('I1','S2',8080,8082), @('I1','S3',8080,8083), @('I1','S4',8080,8084), @('I1','S6',8080,8086),
    @('S1','M1',8081,3306), @('S1','M3',8081,6379), @('S1','S5',8081,8085), @('S1','M5',8081,9092),
    @('S2','M1',8082,3306), @('S2','M3',8082,6379),
    @('S3','M1',8083,3306), @('S3','O1',8083,$null),
    @('S4','E1',8084,$null), @('S4','M1',8084,3306),
    @('S5','M3',8085,6379), @('S5','M1',8085,3306),
    @('S6','M5',8086,9092), @('S6','E2',8086,$null)
)
foreach ($d in $deps) { AddEdge 'depends_on' $d[0] $d[1] $d[2] $d[3] }

# 12) 冗余组
Post "/api/projects/$projectId/redundancy-groups" @{
    name = 'MySQL 主备'; redundancyType = 'active_standby'; description = '数据库主备'
    members = @(
        @{ nodeId = $nodeIds['M1']; role = 'primary' },
        @{ nodeId = $nodeIds['M2']; role = 'standby'; priority = 1 }
    )
} | Out-Null
Post "/api/projects/$projectId/redundancy-groups" @{
    name = '网关双活'; redundancyType = 'active_active'; description = '双活入口'
    members = @(
        @{ nodeId = $nodeIds['G2']; role = 'active' },
        @{ nodeId = $nodeIds['G3']; role = 'active' }
    )
} | Out-Null
Write-Host "  冗余组 2 个"

# 13) 校验
$v = Post "/api/projects/$projectId/validate" @{}
$res = $v.data.result
Write-Host ""
Write-Host ("校验结果：runnable={0} score={1} 错误={2} 警告={3} 提示={4}" -f $res.runnable, $res.score, $res.errorCount, $res.warnCount, $res.infoCount)
$res.items | ForEach-Object { Write-Host ("  [{0}] {1} {2}" -f $_.severity, $_.ruleCode, $_.message) }
Write-Host ""
Write-Host "PROJECT_ID=$projectId"
