<script setup lang="ts">
import { useUiI18n } from '../i18n'
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

const { t } = useUiI18n()
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
        <p class="eyebrow">{{ t('labelFlowCanvas') }}</p>
        <h3>{{ t('labelFlowCanvas') }}</h3>
      </div>
      <div class="graph-meta">
        <span>{{ t('labelNodes') }} {{ nodes.length }}</span>
        <span>{{ t('labelConnections') }} {{ graph.edges.length }}</span>
      </div>
    </div>

    <div class="graph-stage">
      <svg
        class="graph-svg"
        :viewBox="`0 0 ${graph.canvasWidth} ${graph.canvasHeight}`"
        :style="{ minWidth: `${graph.canvasWidth}px`, minHeight: `${graph.canvasHeight}px` }"
        role="img"
        :aria-label="t('graphAria')"
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
            <path class="graph-arrow" d="M0,0 L0,6 L9,3 z" />
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
            rx="8"
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
            {{ t('labelActions') }} {{ node.node.choices.length }} · {{ node.node.speakerId }}
          </text>
        </g>
      </svg>
    </div>

    <p class="graph-hint">
      {{ t('flowCanvasHint') }}
    </p>
  </section>
</template>

<style scoped>
.graph-panel{display:grid;gap:16px}
.graph-head{display:flex;align-items:flex-start;justify-content:space-between;gap:12px;flex-wrap:wrap}
.eyebrow{margin:0 0 8px;font-size:12px;letter-spacing:0;color:var(--color-primary-strong);font-weight:800}
h3,p{margin:0}
h3{font-size:15px;color:var(--color-heading-soft)}
.graph-meta{display:flex;gap:8px;flex-wrap:wrap}
.graph-meta span{padding:5px 9px;border-radius:var(--radius);background:var(--color-token-muted-bg);color:var(--color-faint);font-size:12px;font-weight:700}
.graph-stage{overflow:auto;padding:8px;border:1px solid var(--color-border-soft);border-radius:var(--radius);background:var(--color-graph-bg);scrollbar-gutter:stable}
.graph-svg{display:block}
.graph-arrow{fill:var(--color-graph-arrow)}
.graph-edge{fill:none;stroke:var(--color-graph-edge);stroke-width:2;opacity:.82}
.graph-edge.selected{stroke:var(--color-primary);stroke-width:2.6;opacity:1}
.graph-edge.dangling{stroke-dasharray:7 7;opacity:.5}
.graph-node{cursor:pointer}
.graph-node rect{fill:var(--color-graph-node);stroke:var(--color-graph-node-border);stroke-width:1.2;transition:fill 180ms var(--ease),stroke 180ms var(--ease)}
.graph-node.selected rect{fill:var(--color-graph-node-selected);stroke:var(--color-primary);stroke-width:2}
.graph-node.entry rect{stroke:var(--color-graph-entry)}
.graph-node:hover rect{fill:var(--color-surface-muted)}
.scene-text,.meta-text,.title-text{font-family:"Segoe UI","PingFang SC","Microsoft YaHei",sans-serif;pointer-events:none}
.scene-text{font-size:11px;letter-spacing:0;fill:var(--color-primary-strong)}
.title-text{font-size:16px;font-weight:700;fill:var(--color-heading-soft)}
.meta-text{font-size:12px;fill:var(--color-muted)}
.graph-hint{color:var(--color-faint);line-height:1.6}
@media (prefers-reduced-motion:reduce){.graph-node rect{transition:none}}
</style>
