import React from 'react';

import { createTooltip } from '../tooltips';

export function DefaultSidebar(props) {
  const pathname = window.location.pathname;
  const search = (window.location.search || '?').substring(1);
  const base = `/bo/dashboard/services`;
  const rootClassName = (part) =>
    pathname === `/bo/dashboard/${part}` && search === '' ? 'active' : '';
  const className = (part) =>
    base === pathname && search.indexOf(`env=${part}`) > -1 ? 'active' : '';
  return (
    <ul className="nav flex-column nav-sidebar">
      <li>
        <h3 style={{ marginTop: 0 }}>
          <i className="fas fa-cubes" /> Services
        </h3>
      </li>
      <li key="all">
        <a
          href={`/bo/dashboard/services`}
          className={rootClassName('services')}
          {...createTooltip('List all services declared in Otoroshi')}>
          {' '}
          All services
        </a>
      </li>
      {props.lines.map((line) => (
        <li key={line}>
          <a
            href={`/bo/dashboard/services?env=${line}`}
            className={className(line)}
            {...createTooltip(`List all services declared in Otoroshi for line ${line}`)}>
            {' '}
            For line {line}
          </a>
        </li>
      ))}
      <li>
        <a
          href="#"
          onClick={props.addService}
          {...createTooltip('Create a new service descriptor')}>
          <i className="fas fa-plus" /> Add service
        </a>
      </li>
      {props.env && props.env.clevercloud && (
        <li>
          <a
            href="/bo/dashboard/clever"
            className={rootClassName('clever')}
            {...createTooltip(
              'Create a new service descriptor based on an existing Clever Cloud application'
            )}>
            <i className="fas fa-plus" /> Add service from a CleverApp
          </a>
        </li>
      )}
      <li>
        <h3 style={{ marginTop: 20 }}>
          <i className="fas fa-key" /> Apikeys
        </h3>
      </li>
      <li key="all-apikeys">
        <a
          href={`/bo/dashboard/apikeys`}
          className={rootClassName('apikeys')}
          {...createTooltip('List all apikeys declared in Otoroshi')}>
          {' '}
          All apikeys
        </a>
      </li>
      <li>
        <a href={`/bo/dashboard/apikeys/add`} {...createTooltip('Create a new apikey')}>
          <i className="fas fa-plus" /> Add apikey
        </a>
      </li>
      <li>
        <h3 style={{ marginTop: 20 }}>
          <i className="fas fa-cubes" /> Tcp Services
        </h3>
      </li>
      <li key="all-tcp-services">
        <a
          href={`/bo/dashboard/tcp/services`}
          className={rootClassName('tcp-services')}
          {...createTooltip('List all Tcp services declared in Otoroshi')}>
          {' '}
          All services
        </a>
      </li>
      <li>
        <a href={`/bo/dashboard/tcp/services/add`} {...createTooltip('Create a new Tcp service')}>
          <i className="fas fa-plus" /> Add Tcp service
        </a>
      </li>
    </ul>
  );
}
