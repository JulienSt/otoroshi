import React, { Component, Suspense } from 'react';

import {
  ArrayInput,
  ObjectInput,
  BooleanInput,
  SelectInput,
  TextInput,
  TextareaInput,
  NumberInput,
} from './inputs';

const CodeInput = React.lazy(() => Promise.resolve(require('./inputs/CodeInput')));

import deepSet from 'set-value';
import cloneDeep from 'lodash/cloneDeep';
import { Separator } from './Separator';
import { Proxy } from './Proxy';
import { Location } from '../components/Location';
import { Collapse } from '../components/inputs/Collapse';
import { PillButton } from './PillButton';
import * as BackOfficeServices from '../services/BackOfficeServices';

import { YAMLExportButton } from '../components/exporters/YAMLButton';
import { JsonExportButton } from '../components/exporters/JSONButton';
import { SquareButton } from './SquareButton';
import { Dropdown } from './Dropdown';
import { NgForm } from './nginputs';
import JwtVerifierForm from '../forms/entities/JwtVerifier';

export class JwtVerifier extends Component {
  state = {
    isConfigView: true,
    isLegacyView: false,
    verifier: {
      type: 'globale',
      enabled: false,
      strict: true,
      source: { type: 'InHeader', name: 'X-JWT-Token', remove: '' },
      algoSettings: { type: 'HSAlgoSettings', size: 512, secret: 'secret' },
      strategy: {
        type: 'PassThrough',
        verificationSettings: { fields: { iss: 'The Issuer' }, arrayFields: {} },
      },
    }
  }

  render() {
    const { isConfigView, isLegacyView, verifier } = this.state;

    // console.log(verifier)

    return (
      <div>
        <Header
          isConfigView={isConfigView}
          onChange={isConfigView => this.setState({ isConfigView })} />

        <div style={{ width: 'fit-content' }} className="ms-auto mt-3">
          {isConfigView && <PillButton
            style={{
              backgroundColor: '#494949'
            }}
            rightEnabled={!isLegacyView}
            leftText='New form'
            rightText='Legacy form'
            onChange={v => this.setState({ isLegacyView: !v })}
          />}
        </div>

        {isLegacyView &&
          <LegacyJwtVerifier
            verifier={verifier}
            changeTheValue={(name, value) => {
              const path = name.startsWith('.') ? name.substr(1) : name;
              this.setState({
                verifier: deepSet(cloneDeep(verifier), path, value)
              })
            }} />
        }

        {!isLegacyView &&
          <NgForm
            useBreadcrumb={true}
            value={verifier}
            schema={JwtVerifierForm.config_schema}
            flow={JwtVerifierForm.config_flow}
            onChange={verifier => this.setState({ verifier })}
          />
        }
      </div>
    )
  }
}

function Header({ isConfigView, onChange, verifier }) {
  return <div style={{
    position: 'relative'
  }}>
    <PillButton
      style={{
        backgroundColor: '#494949'
      }}
      rightEnabled={isConfigView}
      leftText="Config"
      rightText="Visualization"
      onChange={onChange}
    />
    <div style={{ position: 'absolute', right: 0, top: 0 }}>
      <Dropdown>
        <YAMLExportButton value={verifier} />
        <JsonExportButton value={verifier} />
        <SquareButton
          level="danger"
          onClick={() => {
            const what = window.location.pathname.split('/')[3];
            const id = window.location.pathname.split('/')[4];
            window
              .newConfirm('Delete this verifier ?')
              .then((ok) => {
                if (ok) {
                  BackOfficeServices.deleteJwtVerifier(id)
                    .then(() => {
                      history.push('/' + what);
                    });
                }
              });
          }}
          icon="fa-trash"
          text="Delete" />
      </Dropdown>
    </div>
  </div>
}

export class LocationSettings extends Component {
  state = {
    error: null,
  };

  componentDidCatch(error) {
    const location = this.props.location;
    const path = this.props.path;
    console.log('AlgoSettings did catch', error, path, location);
    this.setState({ error });
  }

  render() {
    const location = this.props.location;
    const path = this.props.path;
    const changeTheValue = this.props.changeTheValue;
    if (this.state.error) {
      return <span>{this.state.error.message ? this.state.error.message : this.state.error}</span>;
    }
    return (
      <div>
        <SelectInput
          label={this.props.locationTitle || 'Source'}
          value={location.type}
          onChange={(e) => {
            switch (e) {
              case 'InQueryParam':
                changeTheValue(path + '', { type: 'InQueryParam', name: 'jwt-token' });
                break;
              case 'InHeader':
                changeTheValue(path + '', { type: 'InHeader', name: 'X-JWT-Token', remove: '' });
                break;
              case 'InCookie':
                changeTheValue(path + '', { type: 'InCookie', name: 'jwt-token' });
                break;
            }
          }}
          possibleValues={[
            { label: 'JWT token location in query string', value: 'InQueryParam' },
            { label: 'JWT token location in a header', value: 'InHeader' },
            { label: 'JWT token location in a cookie', value: 'InCookie' },
          ]}
          help="The location where to find/set the JWT token"
        />
        {location.type === 'InQueryParam' && (
          <TextInput
            label="Query param name"
            placeholder="jwt-token"
            value={location.name}
            help="The name of the query param where JWT is located"
            onChange={(e) => changeTheValue(path + '.name', e)}
          />
        )}
        {location.type === 'InHeader' && [
          <TextInput
            label="Header name"
            placeholder="jwt-token"
            value={location.name}
            help="The name of the header where JWT is located"
            onChange={(e) => changeTheValue(path + '.name', e)}
          />,
          <TextInput
            label={this.props.sign ? `Prepend value` : `Remove value`}
            placeholder="Bearer "
            value={location.remove}
            help={(this.props.sign ? 'Remove' : 'Prepend') + ' a value inside the header value'}
            onChange={(e) => changeTheValue(path + '.remove', e)}
          />,
        ]}
        {location.type === 'InCookie' && (
          <TextInput
            label="Cookie name"
            placeholder="jwt-token"
            value={location.name}
            help="The name of the cookie where JWT is located"
            onChange={(e) => changeTheValue(path + '.name', e)}
          />
        )}
      </div>
    );
  }
}

export class AlgoSettings extends Component {
  state = {
    error: null,
  };

  componentDidCatch(error) {
    const algo = this.props.algo;
    const path = this.props.path;
    console.log('AlgoSettings did catch', error, path, algo);
    this.setState({ error });
  }

  render() {
    const algo = this.props.algo;
    const path = this.props.path;
    const changeTheValue = this.props.changeTheValue;
    if (this.state.error) {
      return <span>{this.state.error.message ? this.state.error.message : this.state.error}</span>;
    }
    return (
      <div>
        <SelectInput
          label={this.props.algoTitle || 'Algo.'}
          value={algo.type}
          onChange={(e) => {
            switch (e) {
              case 'HSAlgoSettings':
                changeTheValue(path + '', {
                  type: 'HSAlgoSettings',
                  size: 512,
                  secret: 'secret',
                });
                break;
              case 'RSAlgoSettings':
                changeTheValue(path + '', {
                  type: 'RSAlgoSettings',
                  size: 512,
                  publicKey: '-----BEGIN PUBLIC KEY-----\nxxxxxxxx\n-----END PUBLIC KEY-----',
                  privateKey: '-----BEGIN PRIVATE KEY-----\nxxxxxxxx\n-----END PRIVATE KEY-----',
                });
                break;
              case 'ESAlgoSettings':
                changeTheValue(path + '', {
                  type: 'ESAlgoSettings',
                  size: 512,
                  publicKey: '-----BEGIN PUBLIC KEY-----\nxxxxxxxx\n-----END PUBLIC KEY-----',
                  privateKey: '-----BEGIN PRIVATE KEY-----\nxxxxxxxx\n-----END PRIVATE KEY-----',
                });
                break;
              case 'JWKSAlgoSettings':
                changeTheValue(path + '', {
                  type: 'JWKSAlgoSettings',
                  url: 'https://jwk.oto.tools/.well-known/jwks.json',
                  headers: {},
                  timeout: 2000,
                  ttl: 5 * 60 * 60 * 1000,
                  kty: 'RSA',
                  mtlsConfig: {
                    mtls: false,
                    loose: false,
                    certs: [],
                  },
                });
                break;
              case 'RSAKPAlgoSettings':
                changeTheValue(path + '', {
                  type: 'RSAKPAlgoSettings',
                  size: 512,
                  certId: null,
                });
                break;
              case 'ESKPAlgoSettings':
                changeTheValue(path + '', {
                  type: 'ESKPAlgoSettings',
                  size: 512,
                  certId: null,
                });
                break;
              case 'KidAlgoSettings':
                changeTheValue(path + '', {
                  type: 'KidAlgoSettings',
                  onlyExposedCerts: false,
                });
                break;
            }
            // changeTheValue(path + '', e)
          }}
          possibleValues={[
            { label: 'Hmac + SHA', value: 'HSAlgoSettings' },
            { label: 'RSASSA-PKCS1 + SHA', value: 'RSAlgoSettings' },
            { label: 'ECDSA + SHA', value: 'ESAlgoSettings' },
            { label: 'JWK Set (only for verification)', value: 'JWKSAlgoSettings' },
            { label: 'RSASSA-PKCS1 + SHA from KeyPair', value: 'RSAKPAlgoSettings' },
            { label: 'ECDSA + SHA from KeyPair', value: 'ESKPAlgoSettings' },
            {
              label: 'Otoroshi KeyPair from token kid (only for verification)',
              value: 'KidAlgoSettings',
            },
          ]}
          help="What kind of algorithm you want to use to verify/sign your JWT token with"
        />
        {algo.type === 'KidAlgoSettings' && [
          <BooleanInput
            label="Use only exposed keypairs"
            value={algo.onlyExposedCerts}
            help="..."
            onChange={(e) => changeTheValue(path + '.onlyExposedCerts', e)}
          />,
        ]}
        {algo.type === 'HSAlgoSettings' && [
          <SelectInput
            label="SHA Size"
            help="Word size for the SHA-2 hash function used"
            value={algo.size}
            onChange={(v) => changeTheValue(path + '.size', v)}
            possibleValues={[
              { label: '256', value: 256 },
              { label: '384', value: 384 },
              { label: '512', value: 512 },
            ]}
          />,
          <TextInput
            label="Hmac secret"
            placeholder="secret"
            value={algo.secret}
            help="The Hmac secret"
            onChange={(e) => changeTheValue(path + '.secret', e)}
          />,
          <BooleanInput
            label="Base64 encoded secret"
            placeholder="secret"
            value={algo.base64}
            help="Is the secret encoded with base64"
            onChange={(e) => changeTheValue(path + '.base64', e)}
          />,
        ]}
        {algo.type === 'RSAlgoSettings' && [
          <SelectInput
            label="SHA Size"
            help="Word size for the SHA-2 hash function used"
            value={algo.size}
            onChange={(v) => changeTheValue(path + '.size', v)}
            possibleValues={[
              { label: '256', value: 256 },
              { label: '384', value: 384 },
              { label: '512', value: 512 },
            ]}
          />,
          <TextareaInput
            label="Public key"
            value={algo.publicKey}
            help="The RSA public key"
            style={{ fontFamily: 'monospace' }}
            onChange={(e) => changeTheValue(path + '.publicKey', e)}
          />,
          <TextareaInput
            label="Private key"
            value={algo.privateKey}
            style={{ fontFamily: 'monospace' }}
            help="The RSA private key, private key can be empty if not used for JWT token signing"
            onChange={(e) => changeTheValue(path + '.privateKey', e)}
          />,
        ]}
        {algo.type === 'RSAKPAlgoSettings' && [
          <SelectInput
            label="SHA Size"
            help="Word size for the SHA-2 hash function used"
            value={algo.size}
            onChange={(v) => changeTheValue(path + '.size', v)}
            possibleValues={[
              { label: '256', value: 256 },
              { label: '384', value: 384 },
              { label: '512', value: 512 },
            ]}
          />,
          <SelectInput
            label="KeyPair"
            help="The keypair used to sign/verify token"
            value={algo.certId}
            onChange={(v) => changeTheValue(path + '.certId', v)}
            valuesFrom="/bo/api/proxy/api/certificates?keypair=true"
            transformer={(a) => ({ value: a.id, label: a.name + ' - ' + a.description })}
          />,
        ]}
        {algo.type === 'ESKPAlgoSettings' && [
          <SelectInput
            label="SHA Size"
            help="Word size for the SHA-2 hash function used"
            value={algo.size}
            onChange={(v) => changeTheValue(path + '.size', v)}
            possibleValues={[
              { label: '256', value: 256 },
              { label: '384', value: 384 },
              { label: '512', value: 512 },
            ]}
          />,
          <SelectInput
            label="KeyPair"
            help="The keypair used to sign/verify token"
            value={algo.certId}
            onChange={(v) => changeTheValue(path + '.certId', v)}
            valuesFrom="/bo/api/proxy/api/certificates?keypair=true"
            transformer={(a) => ({ value: a.id, label: a.name + ' - ' + a.description })}
          />,
        ]}
        {algo.type === 'ESAlgoSettings' && [
          <SelectInput
            label="SHA Size"
            help="Word size for the SHA-2 hash function used"
            value={algo.size}
            onChange={(v) => changeTheValue(path + '.size', v)}
            possibleValues={[
              { label: '256', value: 256 },
              { label: '384', value: 384 },
              { label: '512', value: 512 },
            ]}
          />,
          <TextareaInput
            label="Public key"
            value={algo.publicKey}
            help="The ECDSA public key"
            style={{ fontFamily: 'monospace' }}
            onChange={(e) => changeTheValue(path + '.publicKey', e)}
          />,
          <TextareaInput
            label="Private key"
            value={algo.privateKey}
            style={{ fontFamily: 'monospace' }}
            help="The ECDSA private key, private key can be empty if not used for JWT token signing"
            onChange={(e) => changeTheValue(path + '.privateKey', e)}
          />,
        ]}
        {algo.type === 'JWKSAlgoSettings' && [
          <TextInput
            label="URL"
            value={algo.url}
            help="The JWK Set url"
            onChange={(e) => changeTheValue(path + '.url', e)}
          />,
          <NumberInput
            label="HTTP call timeout"
            suffix="millis."
            value={algo.timeout}
            help="Timeout for fetching the keyset"
            onChange={(e) => changeTheValue(path + '.timeout', e)}
          />,
          <NumberInput
            label="TTL"
            suffix="millis."
            value={algo.ttl}
            help="Cache TTL for the keyset"
            onChange={(e) => changeTheValue(path + '.ttl', e)}
          />,
          <ObjectInput
            label="HTTP Headers"
            value={algo.headers}
            help="The HTTP headers passed"
            onChange={(e) => changeTheValue(path + '.headers', e)}
          />,
          <SelectInput
            label="Key type"
            help="Type of key"
            value={algo.kty}
            onChange={(v) => changeTheValue(path + '.kty', v)}
            possibleValues={[
              { label: 'RSA', value: 'RSA' },
              { label: 'EC', value: 'EC' },
            ]}
          />,
          <Separator title="TLS settings for JWKS fetching" />,
          <BooleanInput
            label="Custom TLS Settings"
            value={algo.mtlsConfig.mtls}
            help="..."
            onChange={(v) => changeTheValue(path + '.mtlsConfig.mtls', v)}
          />,
          <BooleanInput
            label="TLS loose"
            value={algo.mtlsConfig.loose}
            help="..."
            onChange={(v) => changeTheValue(path + '.mtlsConfig.loose', v)}
          />,
          <BooleanInput
            label="Trust all"
            value={algo.mtlsConfig.trustAll}
            help="..."
            onChange={(v) => changeTheValue(path + '.mtlsConfig.trustAll', v)}
          />,
          <ArrayInput
            label="Client certificate"
            placeholder="Choose a client certificate"
            value={algo.mtlsConfig.certs}
            valuesFrom="/bo/api/proxy/api/certificates"
            transformer={(a) => ({
              value: a.id,
              label: (
                <span>
                  <span className="badge bg-success" style={{ minWidth: 63 }}>
                    {a.certType}
                  </span>{' '}
                  {a.name} - {a.description}
                </span>
              ),
            })}
            help="The certificate used when performing a mTLS call"
            onChange={(v) => changeTheValue(path + '.mtlsConfig.certs', v)}
          />,
          <ArrayInput
            label="Trusted certificate"
            placeholder="Choose a trusted certificate"
            value={algo.mtlsConfig.trustedCerts}
            valuesFrom="/bo/api/proxy/api/certificates"
            transformer={(a) => ({
              value: a.id,
              label: (
                <span>
                  <span className="badge bg-success" style={{ minWidth: 63 }}>
                    {a.certType}
                  </span>{' '}
                  {a.name} - {a.description}
                </span>
              ),
            })}
            help="The trusted certificate used when performing a mTLS call"
            onChange={(v) => changeTheValue(path + '.mtlsConfig.trustedCerts', v)}
          />,
          <Separator title="Proxy" />,
          <Proxy value={algo.proxy} onChange={(v) => changeTheValue(path + '.proxy', v)} />,
        ]}
      </div>
    );
  }
}

export class LegacyJwtVerifier extends Component {
  static defaultVerifier = {
    type: 'local',
    enabled: false,
    strict: true,
    source: { type: 'InHeader', name: 'X-JWT-Token', remove: '' },
    algoSettings: { type: 'HSAlgoSettings', size: 512, secret: 'secret' },
    strategy: {
      type: 'PassThrough',
      verificationSettings: { fields: { iss: 'The Issuer' }, arrayFields: {} },
    },
  };

  changeTheValue = (name, value) => {
    // console.log('changeTheValue', name, value);
    if (this.props.onChange) {
      const clone = cloneDeep(this.props.value || this.props.verifier);
      const path = name.startsWith('.') ? name.substr(1) : name;
      const newObj = deepSet(clone, path, value);
      // console.log('changeTheValue', name, path, value, newObj)
      this.props.onChange(newObj);
    } else {
      this.props.changeTheValue(name, value);
    }
  };

  render() {
    const verifier = this.props.value || this.props.verifier;
    const path = this.props.path || '';
    const changeTheValue = this.changeTheValue;
    return (
      <div>
        {verifier.type === 'global' && (
          <>
            <Collapse initCollapsed={false} label="Location" lineEnd={true}>
              <Location
                tenant={verifier._loc.tenant || 'default'}
                onChangeTenant={(v) => this.changeTheValue('_loc.tenant', v)}
                teams={verifier._loc.teams || ['default']}
                onChangeTeams={(v) => this.changeTheValue('_loc.teams', v)}
              />
            </Collapse>
          </>
        )}
        {verifier.type === 'global' && (
          <TextInput
            label="Id"
            placeholder="The verifier Id"
            disabled
            value={verifier.id}
            help="The verifier Id"
            onChange={(e) => changeTheValue(path + '.id', e)}
          />
        )}
        {verifier.type === 'global' && (
          <TextInput
            label="Name"
            placeholder="The verifier name"
            value={verifier.name}
            help="The verifier name"
            onChange={(e) => changeTheValue(path + '.name', e)}
          />
        )}
        {verifier.type === 'global' && (
          <TextInput
            label="Description"
            placeholder="The verifier description"
            value={verifier.desc}
            help="The verifier description"
            onChange={(e) => changeTheValue(path + '.desc', e)}
          />
        )}
        {!this.props.global && (
          <BooleanInput
            label="Enabled"
            value={verifier.enabled}
            help="Is JWT verification enabled for this service"
            onChange={(v) => changeTheValue(path + '.enabled', v)}
          />
        )}
        <BooleanInput
          label="Strict"
          value={verifier.strict}
          help="If not strict, request without JWT token will be allowed to pass"
          onChange={(v) => changeTheValue(path + '.strict', v)}
        />
        <br />
        {/* **************************************************************************************************** */}
        <Separator title="Token location" />
        <LocationSettings
          path={`${path}.source`}
          changeTheValue={this.changeTheValue}
          location={verifier.source}
        />
        <br />
        {/* **************************************************************************************************** */}
        <Separator
          title={
            verifier.strategy.type === 'DefaultToken'
              ? 'Default token signature'
              : 'Token validation'
          }
        />
        <AlgoSettings
          path={`${path}.algoSettings`}
          changeTheValue={this.changeTheValue}
          algo={verifier.algoSettings}
          withJWK
        />
        <br />
        {/* **************************************************************************************************** */}
        <Separator title="Strategy" />
        <SelectInput
          label="Verif. strategy"
          value={verifier.strategy.type}
          onChange={(e) => {
            switch (e) {
              case 'DefaultToken':
                changeTheValue(path + '.strategy', {
                  type: 'DefaultToken',
                  strict: true,
                  token: {
                    iss: 'foo',
                    iat: '${iat}',
                    nbf: '${nbf}',
                  },
                  verificationSettings: {
                    fields: {},
                    arrayFields: {},
                  },
                });
                break;
              case 'PassThrough':
                changeTheValue(path + '.strategy', {
                  type: 'PassThrough',
                  verificationSettings: {
                    fields: {
                      iss: 'The issuer',
                    },
                    arrayFields: {},
                  },
                });
                break;
              case 'Sign':
                changeTheValue(path + '.strategy', {
                  type: 'Sign',
                  verificationSettings: {
                    fields: {
                      iss: 'The issuer',
                    },
                    arrayFields: {},
                  },
                  algoSettings: {
                    type: 'HSAlgoSettings',
                    size: 512,
                    secret: 'secret',
                  },
                });
                break;
              case 'Transform':
                changeTheValue(path + '.strategy', {
                  type: 'Transform',
                  verificationSettings: {
                    fields: {
                      iss: 'The issuer',
                    },
                    arrayFields: {},
                  },
                  algoSettings: {
                    type: 'HSAlgoSettings',
                    size: 512,
                    secret: 'secret',
                  },
                  transformSettings: {
                    location: {
                      type: 'InHeader',
                      name: 'X-JWT-Token',
                      remove: '',
                    },
                    mappingSettings: {
                      map: {
                        foo: 'bar',
                      },
                      values: {
                        newValue: 'foobar',
                      },
                    },
                  },
                });
                break;
            }
          }}
          possibleValues={[
            { label: 'Default JWT token', value: 'DefaultToken' },
            { label: 'Verify JWT token', value: 'PassThrough' },
            { label: 'Verify and re-sign JWT token', value: 'Sign' },
            { label: 'Verify, re-sign and transform JWT token', value: 'Transform' },
          ]}
          help="What kind of strategy is used for JWT token verification. DefaultToken will add a token if no present. PassThrough will only verifiy token signing and fields values if provided. Sign will do the same as PassThrough plus will re-sign the JWT token with the provided algo. settings. Transform will do the same as Sign plus will be able to transform the token."
        />
        {verifier.strategy.type === 'DefaultToken' && [
          <BooleanInput
            label="Strict"
            help="If token already present, the call will fail"
            value={verifier.strategy.strict}
            onChange={(v) => changeTheValue(path + '.strategy.strict', v)}
          />,
          <Suspense fallback={<div>loading ...</div>}>
            <CodeInput
              label="Default value"
              mode="json"
              value={JSON.stringify(verifier.strategy.token, null, 2)}
              onChange={(e) => this.changeTheValue(path + '.strategy.token', JSON.parse(e))}
            />
          </Suspense>,
          <ObjectInput
            label="Verify token fields"
            placeholderKey="Field name"
            placeholderValue="Field value"
            value={verifier.strategy.verificationSettings.fields}
            help="When the JWT token is checked, each field specified here will be verified with the provided value"
            onChange={(v) => changeTheValue(path + '.strategy.verificationSettings.fields', v)}
          />,
          <ObjectInput
            label="Verify token array value"
            placeholderKey="Field name"
            placeholderValue="One or more comma separated values in the array"
            value={verifier.strategy.verificationSettings.arrayFields}
            help="When the JWT token is checked, each field specified here will be verified if the provided value is contained in the array"
            onChange={(v) => changeTheValue(path + '.strategy.verificationSettings.arrayFields', v)}
          />,
        ]}
        {verifier.strategy.type === 'PassThrough' && [
          <ObjectInput
            label="Verify token fields"
            placeholderKey="Field name"
            placeholderValue="Field value"
            value={verifier.strategy.verificationSettings.fields}
            help="When the JWT token is checked, each field specified here will be verified with the provided value"
            onChange={(v) => changeTheValue(path + '.strategy.verificationSettings.fields', v)}
          />,
          <ObjectInput
            label="Verify token array value"
            placeholderKey="Field name"
            placeholderValue="One or more comma separated values in the array"
            value={verifier.strategy.verificationSettings.arrayFields}
            help="When the JWT token is checked, each field specified here will be verified if the provided value is contained in the array"
            onChange={(v) => changeTheValue(path + '.strategy.verificationSettings.arrayFields', v)}
          />,
        ]}
        {verifier.strategy.type === 'Sign' && [
          <ObjectInput
            label="Verify token fields"
            placeholderKey="Field name"
            placeholderValue="Field value"
            value={verifier.strategy.verificationSettings.fields}
            help="When the JWT token is checked, each field specified here will be verified with the provided value"
            onChange={(v) => changeTheValue(path + '.strategy.verificationSettings.fields', v)}
          />,
          <ObjectInput
            label="Verify token array value"
            placeholderKey="Field name"
            placeholderValue="One or more comma separated values in the array"
            value={verifier.strategy.verificationSettings.arrayFields}
            help="When the JWT token is checked, each field specified here will be verified if the provided value is contained in the array"
            onChange={(v) => changeTheValue(path + '.strategy.verificationSettings.arrayFields', v)}
          />,
          <Separator title="Re-sign settings" />,
          <AlgoSettings
            algoTitle="Re-sign algo."
            path={`${path}.strategy.algoSettings`}
            changeTheValue={this.changeTheValue}
            algo={verifier.strategy.algoSettings}
          />,
        ]}
        {verifier.strategy.type === 'Transform' && [
          <ObjectInput
            label="Verify token fields"
            placeholderKey="Field name"
            placeholderValue="Field value"
            value={verifier.strategy.verificationSettings.fields}
            help="When the JWT token is checked, each field specified here will be verified with the provided value"
            onChange={(v) => changeTheValue(path + '.strategy.verificationSettings.fields', v)}
          />,
          <ObjectInput
            label="Verify token array value"
            placeholderKey="Field name"
            placeholderValue="One or more comma separated values in the array"
            value={verifier.strategy.verificationSettings.arrayFields}
            help="When the JWT token is checked, each field specified here will be verified if the provided value is contained in the array"
            onChange={(v) => changeTheValue(path + '.strategy.verificationSettings.arrayFields', v)}
          />,
          <Separator title="Re-sign settings" />,
          <AlgoSettings
            algoTitle="Re-sign algo."
            path={`${path}.strategy.algoSettings`}
            changeTheValue={this.changeTheValue}
            algo={verifier.strategy.algoSettings}
          />,
          <Separator title="Transformation settings" />,
          <LocationSettings
            sign={true}
            locationTitle="Token location"
            path={`${path}.strategy.transformSettings.location`}
            changeTheValue={this.changeTheValue}
            location={verifier.strategy.transformSettings.location}
          />,
          <ObjectInput
            label="Rename token fields"
            placeholderKey="Field name"
            placeholderValue="Field value"
            value={verifier.strategy.transformSettings.mappingSettings.map}
            help="When the JWT token is transformed, it is possible to change a field name, just specify origin field name and target field name"
            onChange={(v) =>
              changeTheValue(path + '.strategy.transformSettings.mappingSettings.map', v)
            }
          />,
          <ObjectInput
            label="Set token fields"
            placeholderKey="Field name"
            placeholderValue="Field value"
            value={verifier.strategy.transformSettings.mappingSettings.values}
            help="When the JWT token is transformed, it is possible to add new field with static values, just specify field name and value"
            onChange={(v) =>
              changeTheValue(path + '.strategy.transformSettings.mappingSettings.values', v)
            }
          />,
          <ArrayInput
            label="Remove token fields"
            placeholder="Field name"
            value={verifier.strategy.transformSettings.mappingSettings.remove}
            help="When the JWT token is transformed, it is possible to remove fields"
            onChange={(v) =>
              changeTheValue(path + '.strategy.transformSettings.mappingSettings.remove', v)
            }
          />,
        ]}
        <Separator title="Verifier metadata" />
        <ArrayInput
          label="Tags"
          value={verifier.tags}
          onChange={(v) => changeTheValue(path + '.tags', v)}
        />
        <ObjectInput
          label="Metadata"
          value={verifier.metadata}
          onChange={(v) => changeTheValue(path + '.metadata', v)}
        />
      </div>
    );
  }
}
