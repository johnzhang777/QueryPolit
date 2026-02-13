import React, { useState, useEffect, useCallback } from 'react';
import {
  Tabs,
  Table,
  Button,
  Modal,
  Form,
  Input,
  Select,
  Space,
  Typography,
  Tag,
  Popconfirm,
  message,
  Card,
  Tooltip,
} from 'antd';
import {
  PlusOutlined,
  DeleteOutlined,
  ReloadOutlined,
  DatabaseOutlined,
  KeyOutlined,
  SettingOutlined,
} from '@ant-design/icons';
import {
  connectionsApi,
  permissionsApi,
  type ConnectionInfo,
  type ConnectionRequest,
  type PermissionInfo,
  type PermissionRequest,
} from '../api/client';

const { Title } = Typography;

// ==================== Connections Tab ====================
const ConnectionsTab: React.FC = () => {
  const [connections, setConnections] = useState<ConnectionInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [modalOpen, setModalOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [refreshingId, setRefreshingId] = useState<number | null>(null);
  const [form] = Form.useForm<ConnectionRequest>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const { data } = await connectionsApi.list();
      setConnections(data);
    } catch {
      message.error('Failed to load connections');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const handleCreate = async (values: ConnectionRequest) => {
    setSubmitting(true);
    try {
      await connectionsApi.create(values);
      message.success('Connection created');
      setModalOpen(false);
      form.resetFields();
      load();
    } catch (err: unknown) {
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        'Failed to create connection';
      message.error(msg);
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async (id: number) => {
    try {
      await connectionsApi.remove(id);
      message.success('Connection deleted');
      load();
    } catch (err: unknown) {
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        'Failed to delete connection';
      message.error(msg);
    }
  };

  const handleRefreshSchema = async (id: number) => {
    setRefreshingId(id);
    try {
      await connectionsApi.refreshSchema(id);
      message.success('Schema refreshed');
      load();
    } catch (err: unknown) {
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        'Failed to refresh schema';
      message.error(msg);
    } finally {
      setRefreshingId(null);
    }
  };

  const typeColor = (type: string) => {
    switch (type) {
      case 'MYSQL':
        return 'blue';
      case 'POSTGRESQL':
        return 'green';
      case 'H2':
        return 'orange';
      default:
        return 'default';
    }
  };

  const columns = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 60 },
    { title: 'Name', dataIndex: 'name', key: 'name' },
    {
      title: 'Type',
      dataIndex: 'type',
      key: 'type',
      width: 120,
      render: (type: string) => <Tag color={typeColor(type)}>{type}</Tag>,
    },
    {
      title: 'URL',
      dataIndex: 'url',
      key: 'url',
      ellipsis: true,
    },
    { title: 'Username', dataIndex: 'username', key: 'username', width: 120 },
    {
      title: 'Schema',
      dataIndex: 'schemaDdl',
      key: 'schemaDdl',
      width: 100,
      render: (ddl: string | null) =>
        ddl ? (
          <Tooltip title={ddl.substring(0, 500)}>
            <Tag color="success">Available</Tag>
          </Tooltip>
        ) : (
          <Tag color="warning">Not extracted</Tag>
        ),
    },
    {
      title: 'Actions',
      key: 'actions',
      width: 180,
      render: (_: unknown, record: ConnectionInfo) => (
        <Space>
          <Tooltip title="Refresh Schema">
            <Button
              icon={<ReloadOutlined />}
              size="small"
              loading={refreshingId === record.id}
              onClick={() => handleRefreshSchema(record.id)}
            />
          </Tooltip>
          <Popconfirm
            title="Delete this connection?"
            description="All related permissions will also be removed."
            onConfirm={() => handleDelete(record.id)}
            okText="Delete"
            okButtonProps={{ danger: true }}
          >
            <Button icon={<DeleteOutlined />} size="small" danger />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <>
      <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'flex-end' }}>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setModalOpen(true)}>
          Add Connection
        </Button>
      </div>

      <Table
        dataSource={connections}
        columns={columns}
        rowKey="id"
        loading={loading}
        pagination={{ pageSize: 10, showTotal: (t) => `${t} connections` }}
        size="middle"
        bordered
      />

      <Modal
        title="Add Database Connection"
        open={modalOpen}
        onCancel={() => {
          setModalOpen(false);
          form.resetFields();
        }}
        footer={null}
        width={560}
      >
        <Form form={form} onFinish={handleCreate} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="name" label="Connection Name" rules={[{ required: true }]}>
            <Input placeholder="e.g. Production MySQL" />
          </Form.Item>
          <Form.Item name="type" label="Database Type" rules={[{ required: true }]}>
            <Select
              placeholder="Select database type"
              options={[
                { value: 'MYSQL', label: 'MySQL' },
                { value: 'POSTGRESQL', label: 'PostgreSQL' },
                { value: 'H2', label: 'H2' },
              ]}
            />
          </Form.Item>
          <Form.Item name="url" label="JDBC URL" rules={[{ required: true }]}>
            <Input placeholder="e.g. jdbc:mysql://localhost:3306/mydb" />
          </Form.Item>
          <Form.Item name="username" label="Username" rules={[{ required: true }]}>
            <Input placeholder="Database username" />
          </Form.Item>
          <Form.Item name="password" label="Password" rules={[{ required: true }]}>
            <Input.Password placeholder="Database password" />
          </Form.Item>
          <Form.Item style={{ marginBottom: 0, textAlign: 'right' }}>
            <Space>
              <Button
                onClick={() => {
                  setModalOpen(false);
                  form.resetFields();
                }}
              >
                Cancel
              </Button>
              <Button type="primary" htmlType="submit" loading={submitting}>
                Create
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
};

// ==================== Permissions Tab ====================
const PermissionsTab: React.FC = () => {
  const [connections, setConnections] = useState<ConnectionInfo[]>([]);
  const [permissions, setPermissions] = useState<PermissionInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [modalOpen, setModalOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [selectedConnectionFilter, setSelectedConnectionFilter] = useState<number | undefined>();
  const [form] = Form.useForm<PermissionRequest>();

  const loadConnections = useCallback(async () => {
    try {
      const { data } = await connectionsApi.list();
      setConnections(data);
    } catch {
      // silent
    }
  }, []);

  const loadPermissions = useCallback(async (connectionId?: number) => {
    setLoading(true);
    try {
      if (connectionId) {
        const { data } = await permissionsApi.byConnection(connectionId);
        setPermissions(data);
      } else {
        // Load all permissions by iterating connections (or load for a default)
        // For simplicity, show a prompt to select a connection
        setPermissions([]);
      }
    } catch {
      message.error('Failed to load permissions');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadConnections();
  }, [loadConnections]);

  useEffect(() => {
    if (selectedConnectionFilter) {
      loadPermissions(selectedConnectionFilter);
    } else {
      setPermissions([]);
      setLoading(false);
    }
  }, [selectedConnectionFilter, loadPermissions]);

  const handleGrant = async (values: PermissionRequest) => {
    setSubmitting(true);
    try {
      await permissionsApi.grant(values);
      message.success('Permission granted');
      setModalOpen(false);
      form.resetFields();
      if (selectedConnectionFilter) loadPermissions(selectedConnectionFilter);
    } catch (err: unknown) {
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        'Failed to grant permission';
      message.error(msg);
    } finally {
      setSubmitting(false);
    }
  };

  const handleRevoke = async (userId: number, connectionId: number) => {
    try {
      await permissionsApi.revoke(userId, connectionId);
      message.success('Permission revoked');
      if (selectedConnectionFilter) loadPermissions(selectedConnectionFilter);
    } catch (err: unknown) {
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        'Failed to revoke permission';
      message.error(msg);
    }
  };

  const connectionName = (connId: number) => {
    const c = connections.find((x) => x.id === connId);
    return c ? c.name : `Connection #${connId}`;
  };

  const columns = [
    { title: 'Permission ID', dataIndex: 'id', key: 'id', width: 120 },
    { title: 'User ID', dataIndex: 'userId', key: 'userId', width: 100 },
    {
      title: 'Connection',
      dataIndex: 'connectionId',
      key: 'connectionId',
      render: (connId: number) => (
        <Space>
          <DatabaseOutlined />
          {connectionName(connId)} (#{connId})
        </Space>
      ),
    },
    {
      title: 'Actions',
      key: 'actions',
      width: 120,
      render: (_: unknown, record: PermissionInfo) => (
        <Popconfirm
          title="Revoke this permission?"
          onConfirm={() => handleRevoke(record.userId, record.connectionId)}
          okText="Revoke"
          okButtonProps={{ danger: true }}
        >
          <Button icon={<DeleteOutlined />} size="small" danger>
            Revoke
          </Button>
        </Popconfirm>
      ),
    },
  ];

  return (
    <>
      <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Select
          style={{ width: 300 }}
          placeholder="Filter by connection..."
          allowClear
          value={selectedConnectionFilter}
          onChange={(val) => setSelectedConnectionFilter(val)}
          options={connections.map((c) => ({
            value: c.id,
            label: `${c.name} (${c.type})`,
          }))}
        />
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setModalOpen(true)}>
          Grant Permission
        </Button>
      </div>

      {!selectedConnectionFilter && (
        <Card style={{ textAlign: 'center', padding: 24 }}>
          <KeyOutlined style={{ fontSize: 32, color: '#999', marginBottom: 8 }} />
          <div style={{ color: '#999' }}>Select a connection above to view its permissions</div>
        </Card>
      )}

      {selectedConnectionFilter && (
        <Table
          dataSource={permissions}
          columns={columns}
          rowKey="id"
          loading={loading}
          pagination={{ pageSize: 10, showTotal: (t) => `${t} permissions` }}
          size="middle"
          bordered
        />
      )}

      <Modal
        title="Grant Permission"
        open={modalOpen}
        onCancel={() => {
          setModalOpen(false);
          form.resetFields();
        }}
        footer={null}
        width={440}
      >
        <Form form={form} onFinish={handleGrant} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            name="userId"
            label="User ID"
            rules={[{ required: true, message: 'Enter the user ID' }]}
          >
            <Input type="number" placeholder="e.g. 2" />
          </Form.Item>
          <Form.Item
            name="connectionId"
            label="Connection"
            rules={[{ required: true, message: 'Select a connection' }]}
          >
            <Select
              placeholder="Select connection"
              options={connections.map((c) => ({
                value: c.id,
                label: `${c.name} (${c.type})`,
              }))}
            />
          </Form.Item>
          <Form.Item style={{ marginBottom: 0, textAlign: 'right' }}>
            <Space>
              <Button
                onClick={() => {
                  setModalOpen(false);
                  form.resetFields();
                }}
              >
                Cancel
              </Button>
              <Button type="primary" htmlType="submit" loading={submitting}>
                Grant
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
};

// ==================== Admin Page ====================
const AdminPage: React.FC = () => {
  return (
    <div>
      <Title level={4} style={{ marginBottom: 24 }}>
        <SettingOutlined style={{ marginRight: 8 }} />
        Admin Dashboard
      </Title>

      <Card>
        <Tabs
          defaultActiveKey="connections"
          items={[
            {
              key: 'connections',
              label: (
                <span>
                  <DatabaseOutlined /> Connections
                </span>
              ),
              children: <ConnectionsTab />,
            },
            {
              key: 'permissions',
              label: (
                <span>
                  <KeyOutlined /> Permissions
                </span>
              ),
              children: <PermissionsTab />,
            },
          ]}
        />
      </Card>
    </div>
  );
};

export default AdminPage;
