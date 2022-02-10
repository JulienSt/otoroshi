import React, { Component } from 'react';
import { Help } from './Help';

export class PasswordInput extends Component {
  render() {
    return <TextInput {...this.props} type="password" />;
  }
}

export class TextInput extends Component {
  onChange = (e) => {
    if (e && e.preventDefault) e.preventDefault();
    this.props.onChange(e.target.value);
  };

  onDrop = (ev) => {
    ev.preventDefault();
    if (ev.dataTransfer.items) {
      for (var i = 0; i < ev.dataTransfer.items.length; i++) {
        if (ev.dataTransfer.items[i].kind === 'file') {
          const file = ev.dataTransfer.items[i].getAsFile();
          file.text().then((text) => this.props.onChange(text));
        }
      }
    } else {
      for (var i = 0; i < ev.dataTransfer.files.length; i++) {
        const file = ev.dataTransfer.files[i];
        file.text().then((text) => this.props.onChange(text));
      }
    }
  };

  render() {
    if (this.props.hide) {
      return null;
    }
    return (
      <div className="row mb-3">
        <label htmlFor={`input-${this.props.label}`} className="col-xs-12 col-sm-2 col-form-label">
          {this.props.label} <Help text={this.props.help} />
        </label>
        <div className="col-sm-10" style={{ display: 'flex' }}>
          {(this.props.prefix || this.props.suffix) && (
            <div className="input-group">
              {this.props.prefix && <div className="input-group-addon">{this.props.prefix}</div>}
              <input
                type={this.props.type || 'text'}
                className="form-control"
                disabled={this.props.disabled}
                id={`input-${this.props.label}`}
                placeholder={this.props.placeholder}
                value={this.props.value || ''}
                onChange={this.onChange}
                onDrop={this.props.onDrop || this.onDrop}
                onDragOver={(e) => e.preventDefault()}
              />
              {this.props.suffix && <div className="input-group-addon">{this.props.suffix}</div>}
            </div>
          )}
          {!(this.props.prefix || this.props.suffix) && (
            <input
              type={this.props.type || 'text'}
              className="form-control"
              disabled={this.props.disabled}
              id={`input-${this.props.label}`}
              placeholder={this.props.placeholder}
              value={this.props.value || ''}
              onChange={this.onChange}
              onDrop={this.props.onDrop || this.onDrop}
              onDragOver={(e) => e.preventDefault()}
            />
          )}
          {!!this.props.after && this.props.after()}
        </div>
      </div>
    );
  }
}

export class TextareaInput extends Component {
  onChange = (e) => {
    if (e && e.preventDefault) e.preventDefault();
    this.props.onChange(e.target.value);
  };

  onDrop = (ev) => {
    ev.preventDefault();
    if (ev.dataTransfer.items) {
      for (var i = 0; i < ev.dataTransfer.items.length; i++) {
        if (ev.dataTransfer.items[i].kind === 'file') {
          const file = ev.dataTransfer.items[i].getAsFile();
          file.text().then((text) => this.props.onChange(text));
        }
      }
    } else {
      for (var i = 0; i < ev.dataTransfer.files.length; i++) {
        const file = ev.dataTransfer.files[i];
        file.text().then((text) => this.props.onChange(text));
      }
    }
  };

  render() {
    return (
      <div className="row mb-3">
        <label htmlFor={`input-${this.props.label}`} className="col-xs-12 col-sm-2 col-form-label">
          {this.props.label} <Help text={this.props.help} />
        </label>
        <div className="col-sm-10">
          <textarea
            className="form-control"
            disabled={this.props.disabled}
            id={`input-${this.props.label}`}
            placeholder={this.props.placeholder}
            value={this.props.value || ''}
            onChange={this.onChange}
            style={this.props.style}
            rows={this.props.rows || 3}
            onDrop={this.props.onDrop || this.onDrop}
            onDragOver={(e) => e.preventDefault()}
          />
        </div>
      </div>
    );
  }
}

export class RangeTextInput extends Component {
  onChangeFrom = (e) => {
    if (e && e.preventDefault) e.preventDefault();
    this.props.onChangeFrom(e.target.value);
  };
  onChangeTo = (e) => {
    if (e && e.preventDefault) e.preventDefault();
    this.props.onChangeTo(e.target.value);
  };

  onDropFrom = (ev) => {
    ev.preventDefault();
    if (ev.dataTransfer.items) {
      for (var i = 0; i < ev.dataTransfer.items.length; i++) {
        if (ev.dataTransfer.items[i].kind === 'file') {
          const file = ev.dataTransfer.items[i].getAsFile();
          file.text().then((text) => this.props.onChangeFrom(text));
        }
      }
    } else {
      for (var i = 0; i < ev.dataTransfer.files.length; i++) {
        const file = ev.dataTransfer.files[i];
        file.text().then((text) => this.props.onChangeFrom(text));
      }
    }
  };

  onDropTo = (ev) => {
    ev.preventDefault();
    if (ev.dataTransfer.items) {
      for (var i = 0; i < ev.dataTransfer.items.length; i++) {
        if (ev.dataTransfer.items[i].kind === 'file') {
          const file = ev.dataTransfer.items[i].getAsFile();
          file.text().then((text) => this.props.onChangeTo(text));
        }
      }
    } else {
      for (var i = 0; i < ev.dataTransfer.files.length; i++) {
        const file = ev.dataTransfer.files[i];
        file.text().then((text) => this.props.onChangeTo(text));
      }
    }
  };

  render() {
    return (
      <div className="row mb-3">
        <label htmlFor={`input-${this.props.label}`} className="col-xs-12 col-sm-2 col-form-label">
          {this.props.label} <Help text={this.props.help} />
        </label>
        <div className="col-sm-10" style={{ display: 'flex' }}>
          {(this.props.prefixFrom || this.props.suffixFrom) && (
            <div className="input-group col-sm-6">
              {this.props.prefixFrom && (
                <div className="input-group-addon">{this.props.prefixFrom}</div>
              )}
              <input
                type={this.props.typeFrom || 'text'}
                className="form-control"
                disabled={this.props.disabled}
                id={`input-${this.props.label}`}
                placeholder={this.props.placeholderFrom}
                value={this.props.valueFrom || ''}
                onChange={this.onChangeFrom}
                onDrop={this.props.onDropFrom || this.onDropFrom}
                onDragOver={(e) => e.preventDefault()}
              />
              {this.props.suffixFrom && (
                <div className="input-group-addon">{this.props.suffixFrom}</div>
              )}
            </div>
          )}
          {(this.props.prefixTo || this.props.suffixTo) && (
            <div className="input-group col-sm-6">
              {this.props.prefixTo && (
                <div className="input-group-addon">{this.props.prefixTo}</div>
              )}
              <input
                type={this.props.typeTo || 'text'}
                className="form-control"
                disabled={this.props.disabled}
                id={`input-${this.props.label}`}
                placeholder={this.props.placeholderTo}
                value={this.props.valueTo || ''}
                onChange={this.onChangeTo}
                onDrop={this.props.onDropTo || this.onDropTo}
                onDragOver={(e) => e.preventDefault()}
              />
              {this.props.suffixTo && (
                <div className="input-group-addon">{this.props.suffixTo}</div>
              )}
            </div>
          )}
          {!(this.props.prefixFrom || this.props.suffixFrom) && (
            <div style={{ width: '50%' }}>
              <input
                type={this.props.typeFrom || 'text'}
                className="form-control col-sm-6"
                disabled={this.props.disabled}
                id={`input-${this.props.label}`}
                placeholder={this.props.placeholderFrom}
                value={this.props.valueFrom || ''}
                onChange={this.onChangeFrom}
                onDrop={this.props.onDropFrom || this.onDropFrom}
                onDragOver={(e) => e.preventDefault()}
              />
            </div>
          )}
          {!(this.props.prefixTo || this.props.suffixTo) && (
            <div style={{ width: '50%' }}>
              <input
                type={this.props.typeTo || 'text'}
                className="form-control col-sm-6"
                disabled={this.props.disabled}
                id={`input-${this.props.label}`}
                placeholder={this.props.placeholderTo}
                value={this.props.valueTo || ''}
                onChange={this.onChangeTo}
                onDrop={this.props.onDropTo || this.onDropTo}
                onDragOver={(e) => e.preventDefault()}
              />
            </div>
          )}
        </div>
      </div>
    );
  }
}

export class VerticalTextInput extends Component {
  onChange = (e) => {
    if (e && e.preventDefault) e.preventDefault();
    this.props.onChange(e.target.value);
  };
  onDrop = (ev) => {
    ev.preventDefault();
    if (ev.dataTransfer.items) {
      for (var i = 0; i < ev.dataTransfer.items.length; i++) {
        if (ev.dataTransfer.items[i].kind === 'file') {
          const file = ev.dataTransfer.items[i].getAsFile();
          file.text().then((text) => this.props.onChange(text));
        }
      }
    } else {
      for (var i = 0; i < ev.dataTransfer.files.length; i++) {
        const file = ev.dataTransfer.files[i];
        file.text().then((text) => this.props.onChange(text));
      }
    }
  };

  render() {
    return (
      <div className="row mb-3">
        <div className="col-xs-12">
          <label htmlFor={`input-${this.props.label}`} className="col-form-label">
            {this.props.label} <Help text={this.props.help} />
          </label>
          <div>
            {(this.props.prefix || this.props.suffix) && (
              <div className="input-group">
                {this.props.prefix && <div className="input-group-addon">{this.props.prefix}</div>}
                <input
                  type={this.props.type || 'text'}
                  className="form-control"
                  disabled={this.props.disabled}
                  id={`input-${this.props.label}`}
                  placeholder={this.props.placeholder}
                  value={this.props.value || ''}
                  onChange={this.onChange}
                  onDrop={this.props.onDrop || this.onDrop}
                  onDragOver={(e) => e.preventDefault()}
                />
                {this.props.suffix && <div className="input-group-addon">{this.props.suffix}</div>}
              </div>
            )}
            {!(this.props.prefix || this.props.suffix) && (
              <input
                type={this.props.type || 'text'}
                className="form-control"
                disabled={this.props.disabled}
                id={`input-${this.props.label}`}
                placeholder={this.props.placeholder}
                value={this.props.value || ''}
                onChange={this.onChange}
                onDrop={this.props.onDrop || this.onDrop}
                onDragOver={(e) => e.preventDefault()}
              />
            )}
          </div>
        </div>
      </div>
    );
  }
}
