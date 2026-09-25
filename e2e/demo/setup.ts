import { spawn } from 'node:child_process'
import { writeFileSync } from 'node:fs'
import { FORWARDS, STATE } from './env'

/** Opens a port-forward to each internal API the storyline calls; teardown.ts closes them. */
export default async function setup() {
    const pids: number[] = []
    for (const [deployment, local, remote] of FORWARDS) {
        const child = spawn('kubectl', ['-n', 'ec-demo1', 'port-forward', `deploy/${deployment}`, `${local}:${remote}`],
            { detached: true, stdio: 'ignore' })
        child.unref()
        pids.push(child.pid!)
    }
    writeFileSync(STATE, JSON.stringify({ pids }))
    await new Promise(r => setTimeout(r, 5_000))
}
