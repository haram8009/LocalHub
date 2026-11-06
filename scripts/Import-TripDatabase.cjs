const fs = require('node:fs')
const path = require('node:path')
const { spawnSync } = require('node:child_process')
const root = path.resolve(__dirname, '..')
function readEnv(file) {
  if (!fs.existsSync(file)) return {}
  return Object.fromEntries(fs.readFileSync(file,'utf8').split(/\r?\n/).filter(l=>/^\w+=/.test(l)).map(l=>{
    const pos=l.indexOf('='); return [l.slice(0,pos),l.slice(pos+1).replace(/^(['"])(.*)\1$/, '$2')]
  }))
}
const env={...readEnv(path.join(root,'backend/.env')),...readEnv(path.join(root,'.env')),...process.env}
const url=new URL((env.DB_URL || 'jdbc:mysql://localhost:3306/ssafy_trip').replace(/^jdbc:/,''))
const mysql=env.MYSQL_EXE || 'C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe'
const args=['--host='+url.hostname,'--port='+(url.port||'3306'),'--user='+(env.DB_USERNAME||'ssafy'),'--default-character-set=utf8mb4','--batch']
function run(sql) {
  const r=spawnSync(mysql,args,{input:sql,encoding:'utf8',maxBuffer:16*1024*1024,env:{...process.env,MYSQL_PWD:env.DB_PASSWORD||''}})
  if(r.status!==0) throw new Error(r.stderr || 'MySQL execution failed')
  return r.stdout
}
if(url.pathname!='/ssafy_trip') throw new Error('This import only targets ssafy_trip; check DB_URL.')
const count=run("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='ssafy_trip';").trim().split('\n').pop()
if(Number(count)!==0) throw new Error('ssafy_trip contains tables. Import stopped to preserve existing data.')
run(fs.readFileSync(path.join(root,'database/01-schema.sql'),'utf8'))
run(fs.readFileSync(path.join(root,'database/original/SSAFY_TRIP_Dump.sql'),'utf8'))
console.log(run(fs.readFileSync(path.join(root,'database/02-verify.sql'),'utf8')))
