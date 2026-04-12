<script setup lang="ts">
import type { StoryNodeView } from '../types/game'

type PositionedNode = {
  id: string
  node: StoryNodeView
  x: number
  y: number
  width: number
  height: number
  depth: number
}

type GraphEdge = {
  id: string
  sourceId: string
  targetId: string
  label: string
  path: string
  isDangling: boolean
}

const props = defineProps<{
  nodes: StoryNodeView[]
  entryNodeId: string
  selectedNodeId: string
}>()

const emit = defineEmits<{
  selectNode: [nodeId: string]
}>()

const CARD_WIDTH = 220
const CARD_HEIGHT = 110
const COLUMN_GAP = 92
const ROW_GAP = 34
const PADDING_X = 36
const PADDING_Y = 36

const graph = computed(() => {
  const nodeMap = new Map(props.nodes.map(node => [node.id, node]))
  const entryId = nodeMap.has(props.entryNodeId)
    ? props.entryNodeId
    : props.nodes[0]?.id ?? ''

  const depthMap = new Map<string, number>()
  if (entryId) {
    const queue: string[] = [entryId]
    depthMap.set(entryId, 0)

    while (queue.length > 0) {
      const currentId = queue.shift()!
      const currentNode = nodeMap.get(currentId)
      if (!currentNode) continue

      currentNode.choices.forEach((choice) => {
        if (!nodeMap.has(choice.targetNodeId)) return
        if (depthMap.has(choice.targetNodeId)) return
        depthMap.set(choice.targetNodeId, depthMap.get(currentId)! + 1)
        queue.push(choice.targetNodeId)
      })
    }
  }

  let fallbackDepth = depthMap.size > 0
    ? Math.max(...depthMap.values()) + 1
    : 0

  props.nodes.forEach((node) => {
    if (!depthMap.has(node.id)) {
      depthMap.set(node.id, fallbackDepth)
      fallbackDepth += 1
    }
  })

  const columns = new Map<number, StoryNodeView[]>()
  props.nodes.forEach((node) => {
    const depth = depthMap.get(node.id) ?? 0
    const column = columns.get(depth) ?? []
    column.push(node)
    columns.set(depth, column)
  })

  columns.forEach((columnNodes) => {
    columnNodes.sort((left, right) => {
      if (left.sceneId !== right.sceneId) return left.sceneId.localeCompare(right.sceneId)
      return left.title.localeCompare(right.title)
    })
  })

  const positionedNodes = new Map<string, PositionedNode>()
  const orderedDepths = Array.from(columns.keys()).sort((a, b) => a - b)

  orderedDepths.forEach((depth) => {
    const columnNodes = columns.get(depth) ?? []
    columnNodes.forEach((node, index) => {
      positionedNodes.set(node.id, {
        id: node.id,
        node,
        x: PADDING_X + depth * (CARD_WIDTH + COLUMN_GAP),
        y: PADDING_Y + index * (CARD_HEIGHT + ROW_GAP),
        width: CARD_WIDTH,
        height: CARD_HEIGHT,
        depth
      })
    })
  })

  const positionedList = Array.from(positionedNodes.values())
  const canvasWidth = positionedList.length === 0
    ? 720
    : Math.max(...positionedList.map(node => node.x + node.width)) + PADDING_X
  const canvasHeight = positionedList.length === 0
    ? 420
    : Math.max(...positionedList.map(node => node.y + node.height)) + PADDING_Y

  const edges: GraphEdge[] = []
  props.nodes.forEach((node) => {
    const source = positionedNodes.get(node.id)
    if (!source) return

    node.choices.forEach((choice) => {
      const target = positionedNodes.get(choice.targetNodeId)
      const startX = source.x + source.width
      const startY = source.y + source.height / 2
      const endX = target ? target.x : startX + 110
      const endY = target ? target.y + target.height / 2 : startY
      const bend = Math.max(48, Math.abs(endX - startX) * 0.36)
      const path = `M ${startX} ${startY} C ${startX + bend} ${startY}, ${endX - bend} ${endY}, ${endX} ${endY}`

      edges.push({
        id: `${node.id}:${choice.id}`,
        sourceId: node.id,
        targetId: choice.targetNodeId,
        label: choice.label,
        path,
        isDangling: !target
      })
    })
  })

  return {
    entryId,
    nodes: positionedList,
    edges,
    canvasWidth,
    canvasHeight
  }
})

function shortLabel(value: string, length = 28) {
  return value.length <= length ? value : `${value.slice(0, length - 1)}…`
}
</script>

<template>
  <section class="graph-panel">
    <div class="graph-head">
      <div>
        <p class="eyebrow">Canvas</p>
        <h3>节点关系画布</h3>
      </div>
      <div class="graph-meta">
        <span>{{ nodes.length }} 节点</span>
        <span>{{ graph.edges.length }} 连线</span>
      </div>
    </div>

    <div class="graph-stage">
      <svg
        class="graph-svg"
        :viewBox="`0 0 ${graph.canvasWidth} ${graph.canvasHeight}`"
        :style="{ minWidth: `${graph.canvasWidth}px`, minHeight: `${graph.canvasHeight}px` }"
        role="img"
        aria-label="Story graph canvas"
      >
        <defs>
          <marker
            id="graph-arrow"
            markerWidth="10"
            markerHeight="10"
            refX="8"
            refY="3"
            orient="auto"
            markerUnits="strokeWidth"
          >
            <path d="M0,0 L0,6 L9,3 z" fill="#c5b59a" />
          </marker>
        </defs>

        <path
          v-for="edge in graph.edges"
          :key="edge.id"
          :d="edge.path"
          class="graph-edge"
          :class="{
            selected: edge.sourceId === selectedNodeId,
            dangling: edge.isDangling
          }"
          marker-end="url(#graph-arrow)"
        />

        <g
          v-for="node in graph.nodes"
          :key="node.id"
          class="graph-node"
          :class="{
            selected: node.id === selectedNodeId,
            entry: node.id === graph.entryId
          }"
          @click="emit('selectNode', node.id)"
        >
          <rect
            :x="node.x"
            :y="node.y"
            :width="node.width"
            :height="node.height"
            rx="18"
          />
          <text :x="node.x + 16" :y="node.y + 24" class="scene-text">
            {{ shortLabel(node.node.sceneId, 24) }}
          </text>
          <text :x="node.x + 16" :y="node.y + 52" class="title-text">
            {{ shortLabel(node.node.title, 20) }}
          </text>
          <text :x="node.x + 16" :y="node.y + 74" class="meta-text">
            {{ shortLabel(node.node.id, 24) }}
          </text>
          <text :x="node.x + 16" :y="node.y + 94" class="meta-text">
            {{ node.node.choices.length }} 出口 · {{ node.node.speakerId }}
          </text>
        </g>
      </svg>
    </div>

    <p class="graph-hint">
      点击画布节点会同步选中右侧编辑器。搜索后画布会聚焦当前筛选结果。
    </p>
  </section>
</template>

<style scoped>
.graph-panel{display:grid;gap:16px}
.graph-head{display:flex;align-items:flex-start;justify-content:space-between;gap:12px;flex-wrap:wrap}
.eyebrow{margin:0 0 8px;font-size:12px;letter-spacing:.24em;text-transform:uppercase;color:#b9a988}
h3,p{margin:0}
h3{font-size:15px;color:#efe7d6}
.graph-meta{display:flex;gap:8px;flex-wrap:wrap}
.graph-meta span{padding:6px 10px;border-radius:999px;background:rgba(255,255,255,.05);color:#9f9d95;font-size:12px}
.graph-stage{overflow:auto;padding:8px;border:1px solid rgba(255,255,255,.08);border-radius:24px;background:radial-gradient(circle at top left,rgba(229,199,138,.08),transparent 28%),rgba(255,255,255,.02)}
.graph-svg{display:block}
.graph-edge{fill:none;stroke:rgba(197,181,154,.38);stroke-width:2;opacity:.78}
.graph-edge.selected{stroke:#e5c78a;stroke-width:2.6;opacity:1}
.graph-edge.dangling{stroke-dasharray:7 7;opacity:.5}
.graph-node{cursor:pointer}
.graph-node rect{fill:rgba(20,25,31,.92);stroke:rgba(255,255,255,.08);stroke-width:1.2;transition:fill 160ms ease,stroke 160ms ease,transform 160ms ease}
.graph-node.selected rect{fill:rgba(229,199,138,.12);stroke:rgba(229,199,138,.8);stroke-width:2}
.graph-node.entry rect{stroke:rgba(132,192,132,.7)}
.graph-node:hover rect{fill:rgba(255,255,255,.08)}
.scene-text,.meta-text,.title-text{font-family:"Segoe UI","PingFang SC","Microsoft YaHei",sans-serif;pointer-events:none}
.scene-text{font-size:11px;letter-spacing:.12em;text-transform:uppercase;fill:#a59373}
.title-text{font-size:16px;font-weight:700;fill:#f2eadd}
.meta-text{font-size:12px;fill:#9a978f}
.graph-hint{color:#9d9688;line-height:1.6}
</style>
