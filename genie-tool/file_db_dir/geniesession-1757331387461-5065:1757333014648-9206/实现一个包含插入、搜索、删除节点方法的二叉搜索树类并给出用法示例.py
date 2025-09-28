import pandas as pd

class TreeNode:
    def __init__(self, val):
        self.val = val
        self.left = None
        self.right = None

class BinarySearchTree:
    def __init__(self):
        self.root = None
    
    def insert(self, val):
        def helper(node, val):
            if not node:
                return TreeNode(val)
            if val < node.val:
                node.left = helper(node.left, val)
            elif val > node.val:
                node.right = helper(node.right, val)
            return node
        self.root = helper(self.root, val)
    
    def search(self, val):
        node = self.root
        steps = []
        while node:
            steps.append(node.val)
            if val == node.val:
                return True, steps
            elif val < node.val:
                node = node.left
            else:
                node = node.right
        return False, steps
    
    def delete(self, val):
        def helper(node, val):
            if not node:
                return None
            if val < node.val:
                node.left = helper(node.left, val)
            elif val > node.val:
                node.right = helper(node.right, val)
            else:
                if not node.left:
                    return node.right
                if not node.right:
                    return node.left
                tmp = node.right
                while tmp.left:
                    tmp = tmp.left
                node.val = tmp.val
                node.right = helper(node.right, tmp.val)
            return node
        self.root = helper(self.root, val)
    
    def inorder(self):
        result = []
        def dfs(node):
            if not node:
                return
            dfs(node.left)
            result.append(node.val)
            dfs(node.right)
        dfs(self.root)
        return result

# 示例用法
bst = BinarySearchTree()
for val in [8, 3, 10, 1, 6, 14, 4, 7, 13]:
    bst.insert(val)
insert_order = bst.inorder()
found_6, path_6 = bst.search(6)
found_15, path_15 = bst.search(15)
bst.delete(3)
after_delete = bst.inorder()

# 打印分析结果
print("插入节点后的中序遍历结果:", insert_order)
print("搜索6结果:", found_6, "，路径:", path_6)
print("搜索15结果:", found_15, "，路径:", path_15)
print("删除3后的中序遍历结果:", after_delete)

# 保存文件到指定目录
df = pd.DataFrame({
    "操作": ["插入", "搜索6", "搜索15", "删除3"],
    "结果": [
        str(insert_order),
        f"找到: {found_6}, 路径: {path_6}",
        f"找到: {found_15}, 路径: {path_15}",
        str(after_delete)
    ]
})
df.to_excel("/var/folders/k9/3f4qd9j11nbbd0ym1f5qnmbm0000gn/T/tmpo0hswrk5/output/二叉搜索树示例.xlsx", index=False, engine="openpyxl")