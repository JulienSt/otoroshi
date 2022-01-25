import React, { Component } from 'react';
import * as BackOfficeServices from '../services/BackOfficeServices';
import { Table } from '../components/inputs';
import moment from 'moment';

export class AlertPage extends Component {
  columns = [
    {
      title: 'Date',
      content: (item) => item['@timestamp'],
      cell: (v, item) => moment(item['@timestamp']).format('DD/MM/YYYY HH:mm:ss:SSS'),
    },
    { title: 'User', content: (item) => (item.user || {}).name || '--' },
    { title: 'From', content: (item) => (item.audit || {}).from || '--' },
    { title: 'Alert', content: (item) => item.alert },
    { title: 'Action', content: (item) => (item.audit || {}).action || '--' },
    { title: 'Message', content: (item) => (item.audit || {}).message || '--' },
    {
      title: 'Content',
      content: (item) => item['@timestamp'],
      notFilterable: true,
      style: { textAlign: 'center', width: 70 },
      cell: (v, item) => (
        <button
          type="button"
          className="btn btn-success btn-sm"
          onClick={(e) =>
            window.newAlert(
              <pre style={{ height: 300 }}>{JSON.stringify(item, null, 2)}</pre>,
              'Content'
            )
          }>
          content
        </button>
      ),
    },
  ];

  componentDidMount() {
    this.props.setTitle(`Alert Log`);
  }

  fetchEvents = () => {
    return BackOfficeServices.fetchAlertEvents().then(
      (d) => d,
      (err) => console.error(err)
    );
  };

  render() {
    if (!window.__user.superAdmin) {
      return null;
    }
    return (
      <div>
        <Table
          parentProps={this.props}
          selfUrl="alerts"
          defaultTitle="Alert Log"
          defaultValue={() => ({})}
          defaultSort={this.columns[0].title}
          defaultSortDesc={true}
          itemName="Alert Event"
          formSchema={null}
          formFlow={null}
          columns={this.columns}
          fetchItems={this.fetchEvents}
          showActions={false}
          showLink={false}
          injectTable={(table) => (this.table = table)}
          extractKey={(item) => item['@id']}
        />
      </div>
    );
  }
}
