/**
 * 生成 /test-resources/sample-resume.docx（标准简历样例）。
 *
 * 字段与后端 LLM 期望结构对齐：
 *   {name, phone, email, education[], work[], projects[], skills[], selfIntro}
 *
 * 用法: node tools/gen-sample-resume.js
 * 依赖: 全局 npm i -g docx@9.7.x
 */
const fs = require('fs');
const path = require('path');
const {
  Document, Packer, Paragraph, TextRun, HeadingLevel, AlignmentType, BorderStyle
} = require('docx');

const PROFILE = {
  name: '张明',
  phone: '138-1234-5678',
  email: 'zhangming@example.com',
};

const EDUCATION = [
  {
    school: '清华大学',
    major: '计算机科学与技术',
    degree: '本科',
    startDate: '2014-09',
    endDate: '2018-07',
    description: '主修课程：数据结构、操作系统、计算机网络、数据库系统。GPA 3.8/4.0。',
  },
  {
    school: '北京大学',
    major: '软件工程',
    degree: '硕士',
    startDate: '2018-09',
    endDate: '2020-07',
    description: '研究方向：分布式系统与微服务架构。在 NSDI 2020 发表论文一篇。',
  },
];

const WORK = [
  {
    company: '字节跳动',
    position: '后端工程师',
    startDate: '2020-08',
    endDate: '2023-05',
    description: '负责抖音电商订单中台核心链路设计与开发，主导订单状态机重构，支撑日均亿级订单流转。',
    tags: ['Java', 'Spring Boot', 'MySQL', 'Kafka', 'Redis'],
  },
  {
    company: '美团',
    position: '高级后端工程师',
    startDate: '2023-06',
    endDate: '至今',
    description: '负责外卖履约调度服务的高可用与稳定性建设，推动核心服务从单体迁移到 Service Mesh，QPS 峰值 80 万。',
    tags: ['Java', 'Kubernetes', 'Istio', 'ClickHouse', 'Prometheus'],
  },
];

const PROJECTS = [
  {
    name: '智能客服对话平台',
    role: '技术负责人',
    startDate: '2022-03',
    endDate: '2023-04',
    description: '基于 LLM 构建面向 C 端用户的智能客服系统，覆盖售前售后 30+ 业务场景，平均解决率 78%。',
    techStack: ['Java', 'Spring Boot', 'Python', 'LangChain', 'Milvus'],
  },
  {
    name: '高并发订单中台',
    role: '核心开发',
    startDate: '2020-08',
    endDate: '2022-02',
    description: '从 0 到 1 搭建订单中台，统一多业务线订单模型，TPS 12 万，P99 延迟 35ms。',
    techStack: ['Java', 'MySQL', 'Sharding-JDBC', 'RocketMQ', 'Redis'],
  },
];

const SKILLS = [
  'Java', 'Spring Boot', 'MyBatis-Plus', 'MySQL', 'Redis',
  'Kafka', 'RocketMQ', 'Docker', 'Kubernetes',
  'Vue', 'Python', 'LLM 应用开发',
];

const SELF_INTRO =
  '5 年后端开发经验，专注高并发分布式系统设计与稳定性建设。在字节和美团主导过亿级订单链路与百万 QPS 服务，' +
  '熟悉 Java 全栈技术体系。最近一年深耕 LLM 应用工程化，主导智能客服平台从 0 到 1 落地。期望加入一家重视' +
  '工程师文化的团队，在 AI + 业务方向持续深耕。';

function p(text, opts = {}) {
  return new Paragraph({
    spacing: { before: 40, after: 40 },
    alignment: opts.alignment,
    children: [new TextRun({ text, ...opts.run })],
  });
}

function h(text, level) {
  return new Paragraph({
    heading: level,
    spacing: { before: 240, after: 120 },
    children: [new TextRun({ text, bold: true })],
  });
}

function entry({ label, value }) {
  return new Paragraph({
    spacing: { before: 20, after: 20 },
    children: [
      new TextRun({ text: `${label}：`, bold: true }),
      new TextRun({ text: value }),
    ],
  });
}

function buildSection(title, items, renderItem) {
  const children = [h(title, HeadingLevel.HEADING_2)];
  items.forEach((item) => {
    children.push(...renderItem(item));
  });
  return children;
}

function renderEducation(edu) {
  return [
    new Paragraph({
      spacing: { before: 100, after: 40 },
      children: [
        new TextRun({ text: `${edu.school} · ${edu.major} · ${edu.degree}`, bold: true, size: 24 }),
        new TextRun({ text: `   ${edu.startDate} ~ ${edu.endDate}`, color: '666666' }),
      ],
    }),
    p(edu.description, { run: { color: '444444' } }),
  ];
}

function renderWork(w) {
  return [
    new Paragraph({
      spacing: { before: 100, after: 40 },
      children: [
        new TextRun({ text: `${w.company} · ${w.position}`, bold: true, size: 24 }),
        new TextRun({ text: `   ${w.startDate} ~ ${w.endDate}`, color: '666666' }),
      ],
    }),
    p(w.description, { run: { color: '444444' } }),
    p('技术栈：' + w.tags.join(' / '), { run: { italics: true, color: '666666' } }),
  ];
}

function renderProject(pj) {
  return [
    new Paragraph({
      spacing: { before: 100, after: 40 },
      children: [
        new TextRun({ text: `${pj.name} · ${pj.role}`, bold: true, size: 24 }),
        new TextRun({ text: `   ${pj.startDate} ~ ${pj.endDate}`, color: '666666' }),
      ],
    }),
    p(pj.description, { run: { color: '444444' } }),
    p('技术栈：' + pj.techStack.join(' / '), { run: { italics: true, color: '666666' } }),
  ];
}

const doc = new Document({
  creator: 'Opencode',
  title: 'Sample Resume - 张明',
  styles: {
    default: { document: { run: { font: 'Microsoft YaHei', size: 22 } } },
    paragraphStyles: [
      {
        id: 'Heading1', name: 'Heading 1', basedOn: 'Normal', next: 'Normal', quickFormat: true,
        run: { size: 36, bold: true, font: 'Microsoft YaHei', color: '1d6fd8' },
        paragraph: { spacing: { before: 0, after: 240 }, outlineLevel: 0 },
      },
      {
        id: 'Heading2', name: 'Heading 2', basedOn: 'Normal', next: 'Normal', quickFormat: true,
        run: { size: 26, bold: true, font: 'Microsoft YaHei', color: '1d6fd8' },
        paragraph: { spacing: { before: 240, after: 120 }, outlineLevel: 1 },
      },
    ],
  },
  sections: [{
    properties: {
      page: {
        size: { width: 12240, height: 15840 },
        margin: { top: 1440, right: 1440, bottom: 1440, left: 1440 },
      },
    },
    children: [
      new Paragraph({
        heading: HeadingLevel.HEADING_1,
        alignment: AlignmentType.CENTER,
        children: [new TextRun({ text: PROFILE.name })],
      }),
      new Paragraph({
        alignment: AlignmentType.CENTER,
        spacing: { after: 240 },
        children: [
          new TextRun({ text: `电话：${PROFILE.phone}`, color: '666666' }),
          new TextRun({ text: '   |   ', color: '999999' }),
          new TextRun({ text: `邮箱：${PROFILE.email}`, color: '666666' }),
        ],
      }),
      ...buildSection('教育背景', EDUCATION, renderEducation),
      ...buildSection('工作经历', WORK, renderWork),
      ...buildSection('项目经历', PROJECTS, renderProject),
      h('技能清单', HeadingLevel.HEADING_2),
      p(SKILLS.join(' / ')),
      h('自我介绍', HeadingLevel.HEADING_2),
      p(SELF_INTRO),
    ],
  }],
});

const outDir = path.resolve(__dirname, '..', 'test-resources');
fs.mkdirSync(outDir, { recursive: true });
const outPath = path.join(outDir, 'sample-resume.docx');

Packer.toBuffer(doc).then((buffer) => {
  fs.writeFileSync(outPath, buffer);
  console.log(`[OK] 已生成 ${outPath} (${buffer.length} bytes)`);
});
