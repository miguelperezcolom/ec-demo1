import { execFileSync } from 'node:child_process'
import { existsSync, readFileSync, rmSync } from 'node:fs'
import { join } from 'node:path'
import { STATE } from './env'

/** Closes the port-forwards and — unless E2E_RESET=0 — takes ec1 back to the demo's baseline. */
export default async function teardown() {
    if (existsSync(STATE)) {
        for (const pid of JSON.parse(readFileSync(STATE, 'utf8')).pids as number[]) {
            try { process.kill(pid) } catch { /* already gone */ }
        }
        rmSync(STATE)
    }
    if (process.env.E2E_RESET === '0') {
        console.log('E2E_RESET=0: ec1 left as the run left it')
        return
    }
    console.log('Resetting ec1 to the demo baseline…')
    execFileSync(join(__dirname, '..', '..', 'deploy', 'demo', 'reset.sh'), { stdio: 'inherit', timeout: 1_500_000 })
}
