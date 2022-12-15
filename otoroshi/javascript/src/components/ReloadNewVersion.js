import React, { Component } from 'react';
import * as BackOfficeServices from '../services/BackOfficeServices';

export class ReloadNewVersion extends Component {
  state = {
    display: this.props.debug || false,
    version: null,
    versionClosed: false,
    unlogged: false,
  };

  componentDidMount() {
    // console.log('Mount version loader');
    this.checkVersion();
    this.interval = setInterval(this.checkVersion, 60000);
  }

  componentWillUnmount() {
    // console.log('Unmount version loader');
    if (this.interval) {
      clearInterval(this.interval);
    }
  }

  checkVersion = () => {
    BackOfficeServices.version().then(
      (data) => {
        if (data instanceof Error) {
          console.log('Logged out', e);
          this.setState({ display: true, unlogged: true });
          setTimeout(() => {
            window.location.reload();
          }, 20000);
        } else {
          const { versionClosed, version, display } = this.state;
          if (version && !versionClosed && version !== data.version) {
            this.setState({ display: true, version: data.version });
          } else {
            this.setState({ version: data.version });
          }
        }
      },
      (e) => {
        console.log('error during version check', e);
        this.setState({ display: true, unlogged: true });
        setTimeout(() => {
          window.location.reload();
        }, 20000);
      }
    );
  };

  render() {
    if (!this.state.display) {
      return null;
    }
    if (this.state.unlogged) {
      return (
        <div className="loggedOutVeil">
          <div className="topbar-popup" style={{ justifyContent: 'center' }}>
            <div style={{ display: 'flex', alignItems: 'center', marginLeft: 10 }}>
              Your session has expired and you're now logged out. You will be asked to login in 20
              seconds.
              <button
                type="button"
                className="btn btn-danger btn-sm"
                style={{ marginLeft: 10 }}
                title="Login now !!!"
                onClick={(e) => window.location.reload()}>
                <i className="fas fa-sync" />
              </button>
            </div>
          </div>
        </div>
      );
    }
    return (
      <div className="topbar-popup">
        <div style={{ display: 'flex', alignItems: 'center', marginLeft: 20 }}>
          A new version of Otoroshi has been deployed
          <button
            type="button"
            className="btn btn-danger btn-sm"
            style={{ marginLeft: 10 }}
            onClick={(e) => window.location.reload()}>
            <i className="fas fa-sync" />
          </button>
        </div>
        <button
          type="button"
          className="btn btn-sm"
          style={{ alignSelf: 'flex-start' }}
          onClick={(e) => {
            e.preventDefault();
            this.setState({ display: false, versionClosed: true });
          }}>
          <i className="fas fa-times" />
        </button>
      </div>
    );
  }
}
