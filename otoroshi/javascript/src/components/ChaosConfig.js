import React, { Component } from 'react';

import {
  ObjectInput,
  VerticalObjectInput,
  TextInput,
  VerticalTextInput,
  NumberInput,
  VerticalNumberInput,
} from './inputs';
import { Collapse, Panel } from './inputs/Collapse';

function getOrElse(value, f, def) {
  if (value) {
    return f(value);
  } else {
    return def;
  }
}

function enrichConfig(config) {
  const c = config || { enabled: false };
  if (!c.largeRequestFaultConfig) {
    c.largeRequestFaultConfig = {
      ratio: 0.2,
      additionalRequestSize: 0,
    };
  }
  if (!c.largeResponseFaultConfig) {
    c.largeResponseFaultConfig = {
      ratio: 0.2,
      additionalResponseSize: 0,
    };
  }
  if (!c.latencyInjectionFaultConfig) {
    c.latencyInjectionFaultConfig = {
      ratio: 0.2,
      from: 500,
      to: 1000,
    };
  }
  if (!c.badResponsesFaultConfig) {
    c.badResponsesFaultConfig = {
      ratio: 0.2,
      responses: [
        {
          status: 502,
          body: '{"error":true}',
          headers: {
            'Content-Type': 'application/json',
          },
        },
      ],
    };
  }
  return c;
}

export class ChaosConfig extends Component {
  state = {
    config: enrichConfig({ ...this.props.config }),
  };

  componentWillReceiveProps(nextProps) {
    if (nextProps.config !== this.props.config) {
      const c = { ...nextProps.config };
      this.setState({ config: enrichConfig(c) });
    }
  }

  changeTheValue = (name, value) => {
    if (name.indexOf('.') > -1) {
      const [key1, key2] = name.split('.');
      const newConfig = {
        ...this.state.config,
        [key1]: { ...this.state.config[key1], [key2]: value },
      };
      this.setState(
        {
          config: newConfig,
        },
        () => {
          this.props.onChange(this.state.config);
        }
      );
    } else {
      const newConfig = { ...this.state.config, [name]: value };
      this.setState(
        {
          config: newConfig,
        },
        () => {
          this.props.onChange(this.state.config);
        }
      );
    }
  };

  changeFirstResponse = (name, value) => {
    const newConfig = { ...this.state.config };
    if (!newConfig.badResponsesFaultConfig.responses[0]) {
      newConfig.badResponsesFaultConfig.responses[0] = {
        status: 502,
        body: '{"error":true}',
        headers: {
          'Content-Type': 'application/json',
        },
      };
    }
    newConfig.badResponsesFaultConfig.responses[0][name] = value;
    this.setState(
      {
        config: newConfig,
      },
      () => {
        this.props.onChange(this.state.config);
      }
    );
  };

  displayLabel = (label) => {
    if (this.props.inServiceDescriptor) {
      return `► ${label}`;
    }
    return label;
  };

  render() {
    if (!this.state.config) return null;
    return [
      <Collapse
        collapsed={this.props.collapsed}
        initCollapsed={this.props.initCollapsed}
        label={this.displayLabel('Large Request Fault')}
      >
        <NumberInput
          label="Ratio"
          help="..."
          step="0.1"
          min="0"
          max="1"
          value={this.state.config.largeRequestFaultConfig.ratio}
          onChange={(v) => this.changeTheValue('largeRequestFaultConfig.ratio', v)}
        />
        <NumberInput
          suffix="bytes"
          label="Additional size"
          help="..."
          value={this.state.config.largeRequestFaultConfig.additionalRequestSize}
          onChange={(v) => this.changeTheValue('largeRequestFaultConfig.additionalRequestSize', v)}
        />
      </Collapse>,
      <Collapse
        collapsed={this.props.collapsed}
        initCollapsed={this.props.initCollapsed}
        label={this.displayLabel('Large Response Fault')}
      >
        <NumberInput
          label="Ratio"
          help="..."
          step="0.1"
          min="0"
          max="1"
          value={this.state.config.largeResponseFaultConfig.ratio}
          onChange={(v) => this.changeTheValue('largeResponseFaultConfig.ratio', v)}
        />
        <NumberInput
          suffix="bytes"
          label="Additional size"
          help="..."
          value={this.state.config.largeResponseFaultConfig.additionalResponseSize}
          onChange={(v) =>
            this.changeTheValue('largeResponseFaultConfig.additionalResponseSize', v)
          }
        />
      </Collapse>,
      <Collapse
        collapsed={this.props.collapsed}
        initCollapsed={this.props.initCollapsed}
        label={this.displayLabel('Latency injection Fault')}
      >
        <NumberInput
          label="Ratio"
          help="..."
          step="0.1"
          min="0"
          max="1"
          value={this.state.config.latencyInjectionFaultConfig.ratio}
          onChange={(v) => this.changeTheValue('latencyInjectionFaultConfig.ratio', v)}
        />
        <NumberInput
          suffix="ms."
          label="From"
          help="..."
          value={this.state.config.latencyInjectionFaultConfig.from}
          onChange={(v) => this.changeTheValue('latencyInjectionFaultConfig.from', v)}
        />
        <NumberInput
          suffix="ms."
          label="To"
          help="..."
          value={this.state.config.latencyInjectionFaultConfig.to}
          onChange={(v) => this.changeTheValue('latencyInjectionFaultConfig.to', v)}
        />
      </Collapse>,
      <Collapse
        collapsed={this.props.collapsed}
        initCollapsed={this.props.initCollapsed}
        label={this.displayLabel('Bad response Fault')}
      >
        <NumberInput
          label="Ratio"
          help="..."
          step="0.1"
          min="0"
          max="1"
          value={this.state.config.badResponsesFaultConfig.ratio}
          onChange={(v) => this.changeTheValue('badResponsesFaultConfig.ratio', v)}
        />
        <TextInput
          label="Status"
          help="..."
          value={getOrElse(
            this.state.config.badResponsesFaultConfig.responses[0],
            (i) => i.status,
            502
          )}
          onChange={(v) => this.changeFirstResponse('status', v)}
        />
        <TextInput
          label="Body"
          help="..."
          value={getOrElse(
            this.state.config.badResponsesFaultConfig.responses[0],
            (i) => i.body,
            '{"error":true}'
          )}
          onChange={(v) => this.changeFirstResponse('body', v)}
        />
        <ObjectInput
          label="Headers"
          placeholderKey="Header name (ie.Access-Control-Allow-Origin)"
          placeholderValue="Header value (ie. *)"
          value={getOrElse(
            this.state.config.badResponsesFaultConfig.responses[0],
            (i) => i.headers,
            {
              'Content-Type': 'application/json',
            }
          )}
          help="..."
          onChange={(v) => this.changeFirstResponse('headers', v)}
        />
      </Collapse>,
    ];
  }
}

export class ChaosConfigWithSkin extends Component {
  state = {
    config: enrichConfig({ ...this.props.config }),
  };

  componentWillReceiveProps(nextProps) {
    if (nextProps.config !== this.props.config) {
      const c = { ...nextProps.config };
      this.setState({ config: enrichConfig(c) });
    }
  }

  changeTheValue = (name, value) => {
    if (name.indexOf('.') > -1) {
      const [key1, key2] = name.split('.');
      const newConfig = {
        ...this.state.config,
        [key1]: { ...this.state.config[key1], [key2]: value },
      };
      this.setState(
        {
          config: newConfig,
        },
        () => {
          this.props.onChange(this.state.config);
        }
      );
    } else {
      const newConfig = { ...this.state.config, [name]: value };
      this.setState(
        {
          config: newConfig,
        },
        () => {
          this.props.onChange(this.state.config);
        }
      );
    }
  };

  changeFirstResponse = (name, value) => {
    const newConfig = { ...this.state.config };
    if (!newConfig.badResponsesFaultConfig.responses[0]) {
      newConfig.badResponsesFaultConfig.responses[0] = {
        status: 502,
        body: '{"error":true}',
        headers: {
          'Content-Type': 'application/json',
        },
      };
    }
    newConfig.badResponsesFaultConfig.responses[0][name] = value;
    this.setState(
      {
        config: newConfig,
      },
      () => {
        this.props.onChange(this.state.config);
      }
    );
  };

  displayLabel = (label) => {
    if (this.props.inServiceDescriptor) {
      return `► ${label}`;
    }
    return label;
  };

  render() {
    if (!this.state.config) return null;
    return (
      <div className="row">
        <Panel
          title="Large request fault"
          collapsed={this.props.collapsed}
          initCollapsed={this.props.initCollapsed}
        >
          <VerticalNumberInput
            label="Ratio"
            step="0.1"
            min="0"
            max="1"
            value={this.state.config.largeRequestFaultConfig.ratio}
            onChange={(v) => this.changeTheValue('largeRequestFaultConfig.ratio', v)}
          />
          <VerticalNumberInput
            suffix="bytes"
            label="Additional size"
            value={this.state.config.largeRequestFaultConfig.additionalRequestSize}
            onChange={(v) =>
              this.changeTheValue('largeRequestFaultConfig.additionalRequestSize', v)
            }
          />
        </Panel>
        <Panel
          title="Large response fault"
          collapsed={this.props.collapsed}
          initCollapsed={this.props.initCollapsed}
        >
          <VerticalNumberInput
            label="Ratio"
            step="0.1"
            min="0"
            max="1"
            value={this.state.config.largeResponseFaultConfig.ratio}
            onChange={(v) => this.changeTheValue('largeResponseFaultConfig.ratio', v)}
          />
          <VerticalNumberInput
            suffix="bytes"
            label="Additional size"
            value={this.state.config.largeResponseFaultConfig.additionalResponseSize}
            onChange={(v) =>
              this.changeTheValue('largeResponseFaultConfig.additionalResponseSize', v)
            }
          />
        </Panel>
        <Panel
          title="Latency injection fault"
          collapsed={this.props.collapsed}
          initCollapsed={this.props.initCollapsed}
        >
          <VerticalNumberInput
            label="Ratio"
            step="0.1"
            min="0"
            max="1"
            value={this.state.config.latencyInjectionFaultConfig.ratio}
            onChange={(v) => this.changeTheValue('latencyInjectionFaultConfig.ratio', v)}
          />
          <VerticalNumberInput
            suffix="ms."
            label="From"
            value={this.state.config.latencyInjectionFaultConfig.from}
            onChange={(v) => this.changeTheValue('latencyInjectionFaultConfig.from', v)}
          />
          <VerticalNumberInput
            suffix="ms."
            label="To"
            value={this.state.config.latencyInjectionFaultConfig.to}
            onChange={(v) => this.changeTheValue('latencyInjectionFaultConfig.to', v)}
          />
        </Panel>
        <Panel
          title="Bad response fault"
          collapsed={this.props.collapsed}
          initCollapsed={this.props.initCollapsed}
        >
          <VerticalNumberInput
            label="Ratio"
            step="0.1"
            min="0"
            max="1"
            value={this.state.config.badResponsesFaultConfig.ratio}
            onChange={(v) => this.changeTheValue('badResponsesFaultConfig.ratio', v)}
          />
          <VerticalTextInput
            label="Status"
            value={getOrElse(
              this.state.config.badResponsesFaultConfig.responses[0],
              (i) => i.status,
              502
            )}
            onChange={(v) => this.changeFirstResponse('status', v)}
          />
          <VerticalTextInput
            label="Body"
            value={getOrElse(
              this.state.config.badResponsesFaultConfig.responses[0],
              (i) => i.body,
              '{"error":true}'
            )}
            onChange={(v) => this.changeFirstResponse('body', v)}
          />
          <VerticalObjectInput
            label="Headers"
            placeholderKey="Header name (ie.Access-Control-Allow-Origin)"
            placeholderValue="Header value (ie. *)"
            value={getOrElse(
              this.state.config.badResponsesFaultConfig.responses[0],
              (i) => i.headers,
              {
                'Content-Type': 'application/json',
              }
            )}
            onChange={(v) => this.changeFirstResponse('headers', v)}
          />
        </Panel>
      </div>
    );
  }
}
