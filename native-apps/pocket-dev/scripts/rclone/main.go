// pocket-dev アプリに同梱する rclone の入口。Google ドライブのファイルを直接編集するのに使う機能だけを読み込む
// （公式の rclone.go は backend/all と cmd/all ですべての機能を読み込み、1 つの CPU 向けで約 80MB になる）。
// scripts/build-rclone.sh が rclone のソースの中にコピーしてビルドする。
//
// アプリは `rclone authorize drive` で Google にログインし、`rclone rcd`（遠隔操作の API）を
// 端末内だけで動かして、一覧・読み書き・フォルダの作成・削除・名前の変更を API で頼む。
package main

import (
	_ "github.com/rclone/rclone/backend/drive"
	"github.com/rclone/rclone/cmd"
	_ "github.com/rclone/rclone/cmd/authorize"
	_ "github.com/rclone/rclone/cmd/rcd"
	_ "github.com/rclone/rclone/cmd/version"
	// rcd で使う API（operations/* と、フォルダの名前の変更に使う sync/move）を確実に登録する
	_ "github.com/rclone/rclone/fs/operations"
	_ "github.com/rclone/rclone/fs/sync"
)

func main() {
	cmd.Main()
}
