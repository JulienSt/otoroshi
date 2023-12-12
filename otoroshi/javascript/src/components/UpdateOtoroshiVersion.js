import React, { Component } from 'react';

export class UpdateOtoroshiVersion extends Component {
  render() {
    let show = false;
    let lastPresentation = localStorage.getItem('otoroshi_outdated_popup');
    if (lastPresentation) {
      lastPresentation = JSON.parse(lastPresentation);
      if (lastPresentation.closed_at + 24 * 60 * 60 * 1000 < Date.now()) {
        show = true;
      }
    } else {
      show = true;
    }
    if (this.props.env && this.props.env.version && this.props.env.version.outdated && show) {
      return (
        <div className="topbar-popup">
          <div style={{ position: 'relative' }} className="d-flex align-items-center">
            <button
              type="button"
              className="btn btn-sm"
              style={{ alignSelf: 'flex-end', position: 'absolute', right: -18, top: 4 }}
              onClick={(e) => {
                e.preventDefault();
                localStorage.setItem(
                  'otoroshi_outdated_popup',
                  JSON.stringify({ closed_at: Date.now() })
                );
                this.forceUpdate();
              }}
            >
              <i className="fas fa-times" />
            </button>
            <a
              style={{ alignSelf: 'center' }}
              target="_blank"
              onClick={(e) => {
                localStorage.setItem(
                  'otoroshi_outdated_popup',
                  JSON.stringify({ closed_at: Date.now() })
                );
                this.forceUpdate();
              }}
              href={`https://github.com/MAIF/otoroshi/releases/tag/${this.props.env.version.version_raw}`}
            >
              <i className="fab fa-github" style={{ color: '#856404', marginRight: 5 }}></i>A new
              version of Otoroshi is available ({this.props.env.version.version_raw})
            </a>
          </div>
        </div>
      );
    }
    return null;
  }
}
