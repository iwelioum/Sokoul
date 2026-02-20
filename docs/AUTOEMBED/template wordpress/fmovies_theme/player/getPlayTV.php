<?php
/**
 * get servers
 *
 * @package fmovie
 */
$id = $_GET['id'] ?? null;
$sv = $_GET['sv'] ?? null;
$season = $_GET['s'] ?? null;
$episode = $_GET['e'] ?? null;
$site = "";

if ($sv == "premium") {
$site = '//player.autoembed.cc/embed/tv/'.$id.'/'.$season.'/'.$episode;
//$site = esc_url( home_url() ).'/myflixer.php?imdb='.$id.'&season='.$season.'&episode='.$episode;
} 
if ($sv == "embedru") {
$site = '//2embed.cc/embedtv/' . $id . '&s=' . $season . '&e=' . $episode;
} 
else if ($sv == "superembed") {
$site = esc_url( home_url() ).'/wp-content/plugins/fmovie-core/player/player.php?video_id='.$id.'&tmdb=1&s='.$season.'&e='.$episode;
}
else if ($sv == "vidsrc") {
$site = '//vidsrc.xyz/embed/tv?tmdb='.$id.'&season='.$season.'&episode='.$episode;
}
else if ($sv == "openvids") {

$site = '//openvids.io/tmdb/episode/'.$id.'-'.$season.'-'.$episode; 
}
else if ($sv == "") {
$site = '//vidsrc.to/embed/tv/'.$id.'/'.$season.'/'.$episode;
} 
?>
<!DOCTYPE HTML>
<html>
	<head>
	<meta charset="utf-8" />
	<meta name="robots" content="noindex,nofollow">
	<style>
		body,html{
			padding:0;
			left: 0;
			background: transparent;
		}

		iframe{
			position: fixed;
			top: 0px;
			left: 0px;
			width: 100%;
			height: 100%;
		}
	</style>
</head>
<body>
	<script>
		window.location.href="<?php echo $site; ?>";
	</script>
</body>
</html>