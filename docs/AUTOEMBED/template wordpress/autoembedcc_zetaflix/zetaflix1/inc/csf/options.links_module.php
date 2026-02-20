<?php
/**
 * @since 1.0.0
 * @version 2.0
 */
CSF::createSection(ZETA_OPTIONS,
    array(
        'title'  => __z('Links Module'),
        'parent' => 'settings',
        'icon'   => 'fa fa-minus',
        'fields' => array(
            array(
                'id'       => 'links_access',
                'type'     => 'switcher',
                'title'    => __z('Require Login'),
                'subtitle' => __z('Show links module for registered users only'),
                'default'  => true
            ),
            array(
                'id'    => 'linkslanguages',
                'type'  => 'text',
                'title' => __z('Set languages'),
                'subtitle'  => __z('Add comma separated values'),
                'attributes' => array(
                    'placeholder' => 'English, Spanish, Russian, Italian, Portuguese, Turkish, Bulgarian, Chinese'
                )
            ),
            array(
                'id'    => 'linksquality',
                'type'  => 'text',
                'title' => __z('Set resolutions quality'),
                'subtitle'  => __z('Add comma separated values'),
                'attributes' => array(
                    'placeholder' => '4k 2160p, HD 1440p, HD 1080p, HD 720p, SD 480p, SD 360p, SD 240p'
                )
            ),
            array(
                'id'    => 'linksfrontpublishers',
                'type'  => 'checkbox',
                'title' => __z('Front-End Links publishers'),
                'subtitle'  => __z('Check the user roles that can be published from Front-end'),
                'options' => array(
                    'adm' => __z('Administrator'),
                    'edt' => __z('Editor'),
                    'atr' => __z('Author'),
                    'ctr' => __z('Contributor'),
                    'sbr' => __z('Subscriber')
                ),
                'default' => array('adm','edt','atr','ctr','sbr')
            ),
            array(
                'id'    => 'linkspublishers',
                'type'  => 'checkbox',
                'title' => __z('Auto Publish'),
                'subtitle'  => __z('Mark the roles of users who can post links without being moderated'),
                'options' => array(
                    'adm' => __z('Administrator'),
                    'edt' => __z('Editor'),
                    'atr' => __z('Author'),
                    'ctr' => __z('Contributor'),
                    'sbr' => __z('Subscriber')
                ),
                'default' => array('adm','edt','atr','ctr')
            ),
            array(
                'id'    => 'linksrowshow',
                'type'  => 'checkbox',
                'title' => __z('Show in list'),
                'subtitle'  => __z('Select the items that you want to show in the links table'),
                'options' => array(
                    'qua' => __z('Quality'),
                    'lan' => __z('Language'),
                    'siz' => __z('Size'),
                    'cli' => __z('Clicks'),
                    'add' => __z('Added'),
                    'use' => __z('User')
                ),
                'default' => array('qua','lan','siz','cli','add','use')
            ),
            array(
                'id'    => 'linkshoweditor',
                'type'  => 'checkbox',
                'title' => __z('Links Editor'),
                'label' => __z('Show link editor, if the main entry has not yet been published')
            ),
            array(
                'type'    => 'notice',
                'style'   => 'info',
                'content' => __z('This is not a secure method of adding links, there is a very high probability of data loss.'),
                'dependency' => array('linkshoweditor', '==', true)
            ),
            array(
                'type'    => 'subheading',
                'content' => __z('Redirection page')
            ),
            array(
                'id'    => 'linktimewait',
                'type'  => 'text',
                'title' => __z('Timeout'),
                'subtitle'  => __z('Timeout in seconds before redirecting the page automatically'),
                'default' => '30',
                'attributes' => array(
                    'style' => 'width:100px',
                    'type' => 'number'
                )
            ),
            array(
                'id'    => 'linkoutputtype',
                'type'  => 'radio',
                'title' => __z('Type Output'),
                'subtitle'  => __z('Select an output type upon completion of the wait time'),
                'options' => array(
                    'btn' => __z('Clicking on a button'),
                    'clo' => __z('Redirecting the page automatically')
                ),
                'default' => 'btn',
                'dependency' => array('linktimewait', '>', '0')
            ),
            array(
                'id'    => 'linkbtntext',
                'type'  => 'text',
                'title' => __z('Button text'),
                'subtitle'  => __z('Customize the button'),
                'default' => __z('Continue'),
                'dependency' => array('linkoutputtype|linktimewait', '==|>', 'btn|0')
            ),
            array(
                'id'    => 'linkbtntextunder',
                'type'  => 'text',
                'title' => __z('Text under button'),
                'default' => __z('Click on the button to continue'),
                'dependency' => array('linkoutputtype|linktimewait', '==|>', 'btn|0')
            ),
            array(
                'type'    => 'subheading',
                'content' => __z('Shorteners')
            ),
            array(
                'type' => 'content',
                'content' => '
                    <h3>'.__z('To obtain the link, use the <code>{{url}}</code> tag').'</h3>
                    <p>'.__z('To invalidate this function do not add any shortener').'</p>
                '
            ),
            array(
                'id'   => 'shorteners',
                'type' => 'group',
                'button_title'    => __z('Add new shortener'),
                'accordion_title' => __z('Add new shortener'),
                'fields' => array(
                    array(
                        'id'    => 'short',
                        'type'  => 'text',
                        'attributes' => array(
                            'placeholder' => 'http://short.link/any_parameter/{url}'
                        )
                    )
                )
            )
        )
    )
);
