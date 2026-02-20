<?php 
$logo = get_template_directory_uri(). '/assets/img/logo.png';?>
<!DOCTYPE html>
<html <?php language_attributes(); ?>>

    <head>
		<meta content="text/html; charset=UTF-8" http-equiv="Content-Type">
        <meta charset="<?php bloginfo('charset'); ?>" />
        <meta name="robots" content="noindex, follow">
        <meta name="viewport" content="width=device-width, user-scalable=no, initial-scale=1.0, minimum-scale=1.0, maximum-scale=1.0">
        <title><?php echo wp_kses_post($titl); ?></title>
		  <link rel="stylesheet" href="<?php echo ZETA_URI,'/assets/css/main.link.css'; ?>" />
		  <script src="http://ajax.googleapis.com/ajax/libs/jquery/1.7.1/jquery.min.js" type="text/javascript"></script>
        <script type='text/javascript'>
            var Link = <?php echo $json; ?>;
        </script>
        <?php if($ganl){ ?>
        <script async src="https://www.googletagmanager.com/gtag/js?id=<?php echo $ganl; ?>"></script>
        <script>
            window.dataLayer = window.dataLayer || [];
            function gtag(){dataLayer.push(arguments);}
            gtag('js', new Date());
            gtag('config', '<?php echo $ganl; ?>');
        </script>
        <?php } ?>
        <style type='text/css'>
            :root {
                --main-color:<?php echo $clor; ?>;
            }
        </style>
    </head>
<body>
    <div class="link-redirector">
        <div class="wrapper">
            <div class="redirector-heading">
                <img src="<?php echo $logo;?>">
            </div>
            <div class="redirector-body">
                <h3 class="redirect-msg">You're going to <em>google.com</em></h3>
                <div class="wait-done">
                    <a href="<?php echo $murl; ?>">Continue</a>
                  <a href="<?php echo $prml; ?>" class="prev-lnk"><?php echo $titl; ?></a>
                </div>
                <span class="wait-msg">Please wait until time runs out.</span>
                <div id="countdown"></div>
                <script type="text/javascript" charset="utf-8">
                    jQuery(document).ready(function ($) {
                    var countdown = $("#countdown").countdown360({
                        strokeWidth: 4, 
                        radius: 60,
                        seconds: 30,
                        fontColor: '#FFFFFF',
                        fillStyle: "#1a1a1a",   
                        strokeStyle: "#b60108",
                        fontSize: 40,
                        autostart: true,
                        smooth: true, 
                        seconds: <?php echo $time; ?>,
                        onComplete: function () { 
                            $('.wait-done').fadeIn();
                            $('.wait-msg').hide();
                            $('#countdown').hide();
                            }
                    });
                    countdown.start();
                    console.log('countdown360 ', countdown);
                    });
                </script>
            </div>
            <div class="redirector-foot">
            </div>
        </div>
    </div>


<script src="<?php echo ZETA_URI,'/assets/js/countdown.js'; ?>" type="text/javascript" charset="utf-8"></script>
</body>
</html>

<!--
<!DOCTYPE html>
<html <?php language_attributes(); ?>>
    <head>
        <meta charset="<?php bloginfo('charset'); ?>" />
        <meta name="robots" content="noindex, follow">
        <meta name="viewport" content="width=device-width, user-scalable=no, initial-scale=1.0, minimum-scale=1.0, maximum-scale=1.0">
        <title><?php echo wp_kses_post($titl); ?></title>
        <link rel='stylesheet' id='fonts-css'  href='https://fonts.googleapis.com/css?family=Roboto:400,500' type='text/css' media='all' />
        <link rel='stylesheet' id='link-single'  href='<?php echo ZETA_URI,'/assets/css/front.links'.zeta_devmode().'.css'; ?>' type='text/css' media='all' />
        <script type='text/javascript' src='https://ajax.googleapis.com/ajax/libs/jquery/3.3.1/jquery.min.js'></script>
        <script type='text/javascript'>
            var Link = <?php echo $json; ?>;
        </script>
        <?php if($ganl){ ?>
        <script async src="https://www.googletagmanager.com/gtag/js?id=<?php echo $ganl; ?>"></script>
        <script>
            window.dataLayer = window.dataLayer || [];
            function gtag(){dataLayer.push(arguments);}
            gtag('js', new Date());
            gtag('config', '<?php echo $ganl; ?>');
        </script>
        <?php } ?>
        <style type='text/css'>
            :root {
                --main-color:<?php echo $clor; ?>;
            }
        </style>
    </head>
    <body>
        <div class="content">
            <div class="container">
                <div class="box" style="border-top: solid 3px <?php echo $clor; ?>">
                    <?php if($adst) echo "<div class='ads top'>{$adst}</div>"; ?>
                    <div class="inside">
                        <div class="counter">
                            <span id="counter"><?php echo $time; ?></span>
                            <small><?php _z('Please wait until the time runs out'); ?></small>
                        </div>
                        <a id="link" rel="nofollow" href="<?php echo $murl; ?>" class="btn" style="background-color:<?php echo $clor; ?>"><?php echo $btxt; ?></a>
                        <small class="text"><?php echo $txun; ?></small>
                        <small class="text"><a href="<?php echo $prml; ?>"><?php echo $titl; ?></a></small>
                    </div>
                    <?php if($adsb) echo "<div class='ads bottom'>{$adsb}</div>"; ?>
                </div>
                <?php if($type === __z('Torrent')) { ?>
                    <small class="footer"><?php _z('Get this torrent'); ?></small>
                <?php } else { ?>
                    <small class="footer"><?php echo sprintf( __z('Are you going to %s'), '<strong>'.$domn.'</strong>'); ?></small>
                <?php } ?>
            </div>
        </div>
    </body>
    <script type='text/javascript' src='<?php echo ZETA_URI.'/assets/js/front.links'.zeta_devmode().'.js'; ?>'></script>
</html>
-->