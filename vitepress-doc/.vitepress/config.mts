import internalConfig from './config.internal.mts'
import userConfig from './config.user.mts'

const mode = process.env.MRR_DOCS_MODE === 'internal' ? 'internal' : 'user'

export default mode === 'internal' ? internalConfig : userConfig
