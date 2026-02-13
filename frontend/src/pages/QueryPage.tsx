import React, { useState, useEffect } from 'react';
import {
  Card,
  Select,
  Input,
  Button,
  Table,
  Typography,
  Space,
  Tag,
  Alert,
  Spin,
  Empty,
} from 'antd';
import {
  SendOutlined,
  DatabaseOutlined,
  CodeOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import { queryApi, connectionsApi, type ConnectionInfo, type QueryResponse } from '../api/client';
import { useAuth } from '../contexts/AuthContext';

const { Title, Text, Paragraph } = Typography;
const { TextArea } = Input;

const QueryPage: React.FC = () => {
  const { isAdmin } = useAuth();
  const [connections, setConnections] = useState<ConnectionInfo[]>([]);
  const [selectedConnection, setSelectedConnection] = useState<number | undefined>();
  const [question, setQuestion] = useState('');
  const [loading, setLoading] = useState(false);
  const [connectionsLoading, setConnectionsLoading] = useState(true);
  const [response, setResponse] = useState<QueryResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    loadConnections();
  }, []);

  const loadConnections = async () => {
    setConnectionsLoading(true);
    try {
      // Admins use admin endpoint; analysts use user-facing endpoint
      const { data } = isAdmin
        ? await connectionsApi.list()
        : await queryApi.getMyConnections();
      setConnections(data);
      if (data.length > 0 && !selectedConnection) {
        setSelectedConnection(data[0].id);
      }
    } catch {
      setError('Failed to load database connections');
    } finally {
      setConnectionsLoading(false);
    }
  };

  const handleAsk = async () => {
    if (!selectedConnection || !question.trim()) return;

    setLoading(true);
    setError(null);
    setResponse(null);

    try {
      const { data } = await queryApi.ask({
        connectionId: selectedConnection,
        question: question.trim(),
      });
      setResponse(data);
    } catch (err: unknown) {
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        'Query failed. Please try again.';
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  // Build table columns dynamically from the first result row
  const resultColumns =
    response?.result && response.result.length > 0
      ? Object.keys(response.result[0]).map((key) => ({
          title: key,
          dataIndex: key,
          key,
          ellipsis: true,
          render: (val: unknown) => {
            if (val === null || val === undefined) return <Text type="secondary">NULL</Text>;
            return String(val);
          },
        }))
      : [];

  return (
    <div>
      <Title level={4} style={{ marginBottom: 24 }}>
        <ThunderboltOutlined style={{ marginRight: 8 }} />
        Query Playground
      </Title>

      {/* Input Section */}
      <Card style={{ marginBottom: 24 }}>
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          {/* Connection selector */}
          <div>
            <Text strong style={{ display: 'block', marginBottom: 8 }}>
              <DatabaseOutlined style={{ marginRight: 6 }} />
              Database Connection
            </Text>
            <Select
              style={{ width: '100%' }}
              placeholder="Select a database connection"
              value={selectedConnection}
              onChange={setSelectedConnection}
              loading={connectionsLoading}
              options={connections.map((c) => ({
                value: c.id,
                label: (
                  <Space>
                    <Tag color={c.type === 'MYSQL' ? 'blue' : c.type === 'POSTGRESQL' ? 'green' : 'orange'}>
                      {c.type}
                    </Tag>
                    {c.name}
                  </Space>
                ),
              }))}
              notFoundContent={
                connectionsLoading ? (
                  <Spin size="small" />
                ) : (
                  <Empty description="No connections available" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                )
              }
            />
          </div>

          {/* Question input */}
          <div>
            <Text strong style={{ display: 'block', marginBottom: 8 }}>
              <CodeOutlined style={{ marginRight: 6 }} />
              Ask a Question in Natural Language
            </Text>
            <TextArea
              rows={3}
              placeholder="e.g. Show me the top 10 users who registered last month"
              value={question}
              onChange={(e) => setQuestion(e.target.value)}
              onPressEnter={(e) => {
                if (e.ctrlKey || e.metaKey) handleAsk();
              }}
            />
            <Text type="secondary" style={{ fontSize: 12, marginTop: 4, display: 'block' }}>
              Press Ctrl+Enter to submit
            </Text>
          </div>

          <Button
            type="primary"
            icon={<SendOutlined />}
            size="large"
            loading={loading}
            disabled={!selectedConnection || !question.trim()}
            onClick={handleAsk}
          >
            Ask QueryPilot
          </Button>
        </Space>
      </Card>

      {/* Error */}
      {error && (
        <Alert
          message="Error"
          description={error}
          type="error"
          showIcon
          closable
          onClose={() => setError(null)}
          style={{ marginBottom: 24 }}
        />
      )}

      {/* Loading indicator */}
      {loading && (
        <Card style={{ textAlign: 'center', padding: 48, marginBottom: 24 }}>
          <Spin size="large" />
          <Paragraph style={{ marginTop: 16 }}>
            Generating SQL and executing query...
          </Paragraph>
        </Card>
      )}

      {/* Results */}
      {response && !loading && (
        <div>
          {/* Generated SQL */}
          <Card
            title={
              <Space>
                <CodeOutlined />
                Generated SQL
                <Tag color={response.safetyCheck === 'PASSED' ? 'success' : 'error'}>
                  Safety: {response.safetyCheck}
                </Tag>
              </Space>
            }
            style={{ marginBottom: 24 }}
          >
            <pre
              style={{
                background: '#f6f8fa',
                padding: 16,
                borderRadius: 8,
                overflow: 'auto',
                margin: 0,
                fontSize: 14,
                fontFamily: "'SF Mono', 'Fira Code', monospace",
              }}
            >
              {response.sql}
            </pre>
          </Card>

          {/* Result Table */}
          <Card
            title={
              <Space>
                <DatabaseOutlined />
                Query Results
                <Tag>{response.result?.length ?? 0} rows</Tag>
              </Space>
            }
          >
            {response.result && response.result.length > 0 ? (
              <Table
                dataSource={response.result.map((row, i) => ({ ...row, _key: i }))}
                columns={resultColumns}
                rowKey="_key"
                scroll={{ x: 'max-content' }}
                pagination={{
                  pageSize: 20,
                  showSizeChanger: true,
                  showTotal: (total) => `Total ${total} rows`,
                }}
                size="middle"
                bordered
              />
            ) : (
              <Empty description="Query returned no results" />
            )}
          </Card>
        </div>
      )}
    </div>
  );
};

export default QueryPage;
