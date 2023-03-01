const fs = require('fs');

const { InternalProxy, ExternalProxy } = require('./proxy');

const EXTERNAL_PORT = parseInt(process.env.EXTERNAL_PORT || '8443', 10);
const INTERNAL_PORT = parseInt(process.env.INTERNAL_PORT || '8080', 10);
const REQUEST_CERT = (process.env.REQUEST_CERT || 'true') === 'true';
const DISABLE_TOKENS_CHECK = (process.env.DISABLE_TOKENS_CHECK || 'true') === 'true';
const REJECT_UNAUTHORIZED = (process.env.REJECT_UNAUTHORIZED || 'true') === 'true';
const ENABLE_ORIGIN_CHECK = (process.env.ENABLE_ORIGIN_CHECK || 'true') === 'true';
const DISPLAY_ENV = (process.env.DISPLAY_ENV || 'true') === 'true';
const ENABLE_TRACE = (process.env.ENABLE_TRACE || 'true') === 'true';
const ENABLE_CLIENT_CERT_CHECK = (process.env.ENABLE_CLIENT_CERT_CHECK || 'true') === 'true';

function contextExtractor() {

  function extract() {
    const OTOROSHI_DOMAIN =    process.env.OTOROSHI_DOMAIN || 'otoroshi.mesh';
    const OTOROSHI_HOST =      process.env.OTOROSHI_HOST || 'otoroshi-service.otoroshi.svc.cluster.local';
    const OTOROSHI_PORT =      parseInt(process.env.OTOROSHI_PORT || '8443', 10);
    const LOCAL_PORT =         parseInt(process.env.LOCAL_PORT || '8081', 10);
    const CLIENT_ID_PATH =     process.env.CLIENT_ID_PATH;
    const CLIENT_SECRET_PATH = process.env.CLIENT_SECRET_PATH;
    const CLIENT_CA_PATH =     process.env.CLIENT_CA_PATH;
    const CLIENT_CERT_PATH =   process.env.CLIENT_CERT_PATH;
    const CLIENT_KEY_PATH =    process.env.CLIENT_KEY_PATH;
    const BACKEND_CA_PATH =    process.env.BACKEND_CA_PATH;
    const BACKEND_CERT_PATH =  process.env.BACKEND_CERT_PATH;
    const BACKEND_KEY_PATH =   process.env.BACKEND_KEY_PATH;
    const TOKEN_SECRET =       process.env.TOKEN_SECRET || 'secret';
    const EXPECTED_DN =        process.env.EXPECTED_DN;
    const CLIENT_ID =          process.env.CLIENT_ID || fs.readFileSync(CLIENT_ID_PATH || '/var/run/secrets/kubernetes.io/otoroshi.io/apikeys/clientId').toString('utf8')
    const CLIENT_SECRET =      process.env.CLIENT_SECRET || fs.readFileSync(CLIENT_SECRET_PATH || '/var/run/secrets/kubernetes.io/otoroshi.io/apikeys/clientSecret').toString('utf8')
    const CLIENT_CA =          process.env.CLIENT_CA || fs.readFileSync(CLIENT_CA_PATH || '/var/run/secrets/kubernetes.io/otoroshi.io/certs/client/ca.crt').toString('utf8')
    const CLIENT_CERT =        process.env.CLIENT_CERT || fs.readFileSync(CLIENT_CERT_PATH || '/var/run/secrets/kubernetes.io/otoroshi.io/certs/client/tls.crt').toString('utf8')
    const CLIENT_KEY =         process.env.CLIENT_KEY || fs.readFileSync(CLIENT_KEY_PATH || '/var/run/secrets/kubernetes.io/otoroshi.io/certs/client/tls.key').toString('utf8')
    const BACKEND_CA =         process.env.BACKEND_CA || fs.readFileSync(BACKEND_CA_PATH || '/var/run/secrets/kubernetes.io/otoroshi.io/certs/backend/ca.crt').toString('utf8')
    const BACKEND_CERT =       process.env.BACKEND_CERT || fs.readFileSync(BACKEND_CERT_PATH || '/var/run/secrets/kubernetes.io/otoroshi.io/certs/backend/tls.crt').toString('utf8')
    const BACKEND_KEY =        process.env.BACKEND_KEY || fs.readFileSync(BACKEND_KEY_PATH || '/var/run/secrets/kubernetes.io/otoroshi.io/certs/backend/tls.key').toString('utf8')
    return {
      OTOROSHI_DOMAIN,
      OTOROSHI_HOST,
      OTOROSHI_PORT,
      LOCAL_PORT,
      CLIENT_ID,
      CLIENT_SECRET,
      CLIENT_CA,
      CLIENT_CERT,
      CLIENT_KEY,
      BACKEND_CA,
      BACKEND_CERT,
      BACKEND_KEY,
      TOKEN_SECRET,
      EXTERNAL_PORT,
      INTERNAL_PORT,
      REQUEST_CERT,
      DISABLE_TOKENS_CHECK,
      REJECT_UNAUTHORIZED,
      ENABLE_ORIGIN_CHECK,
      DISPLAY_ENV,
      ENABLE_TRACE,
      ENABLE_CLIENT_CERT_CHECK,
      EXPECTED_DN
    }
  }

  let latest = extract();
  let next = Date.now() + 60000;

  if (DISPLAY_ENV) {
    console.log('current env:', JSON.stringify(latest, null, 2))
  }

  return () => {
    if (Date.now() > next) {
      return latest;
    } else {
      next = Date.now() + 60000;
      latest = extract()
      return latest;
    }
  }
}

const internalProxy = InternalProxy({ 
  rejectUnauthorized: REJECT_UNAUTHORIZED, 
  requestCert: REQUEST_CERT, 
  enableOriginCheck: ENABLE_ORIGIN_CHECK, 
  disableTokensCheck: DISABLE_TOKENS_CHECK,
  enableTrace: ENABLE_TRACE,
  context: contextExtractor()
}).listen({ 
  hostname: '127.0.0.1', 
  port: INTERNAL_PORT 
});

const externalProxy = ExternalProxy({ 
  rejectUnauthorized: REJECT_UNAUTHORIZED, 
  requestCert: REQUEST_CERT, 
  enableOriginCheck: ENABLE_ORIGIN_CHECK, 
  disableTokensCheck: DISABLE_TOKENS_CHECK,
  enableTrace: ENABLE_TRACE,
  context: contextExtractor()
}).listen({ 
  hostname: '0.0.0.0', 
  port: EXTERNAL_PORT 
});