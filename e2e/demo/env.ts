import { readFileSync, existsSync } from 'node:fs'
import { homedir } from 'node:os'
import { join } from 'node:path'

/** KEY=VALUE files, read literally: the values carry & and = that a shell would split. */
export function envFile(name: string): Record<string, string> {
    const path = join(homedir(), '.config', 'ec-demo1', name)
    if (!existsSync(path)) throw new Error(`${path} is missing: the demo e2e needs its credentials`)
    const values: Record<string, string> = {}
    for (const line of readFileSync(path, 'utf8').split('\n')) {
        const i = line.indexOf('=')
        if (i > 0 && !line.trim().startsWith('#')) values[line.slice(0, i).trim()] = line.slice(i + 1).trim()
    }
    return values
}

/** The internal APIs, reached through the port-forwards setup.ts opens. */
export const API = {
    booking: 'http://localhost:28108',
    crs: 'http://localhost:28121',
    mapping: 'http://localhost:28122',
    integrations: 'http://localhost:28126',
    mdm: 'http://localhost:28127',
    frontOffice: 'http://localhost:28128',
}

export const FORWARDS: [string, number, number][] = [
    ['booking', 28108, 8108], ['crs-integration-service', 28121, 8121], ['mapping-service', 28122, 8122],
    ['integrations-service', 28126, 8126], ['customer-mdm-service', 28127, 8127], ['front-office', 28128, 8128],
]

export const STATE = join(__dirname, '..', '.demo-state.json')
