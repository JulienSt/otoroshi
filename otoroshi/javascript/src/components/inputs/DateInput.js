import React, { Component } from 'react';
import { Help } from './Help';

import moment from 'moment';

import { OtoDateTimePicker } from '../datepicker';

export class DateTimeInput extends Component {
  onChange = (e) => {
    if (e && e.preventDefault) e.preventDefault();
    this.props.onChange(e);
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
          <OtoDateTimePicker
            date={this.props.value === '' ? null : moment(this.props.value)}
            onChange={this.onChange}
          />
        </div>
      </div>
    );
  }
}
